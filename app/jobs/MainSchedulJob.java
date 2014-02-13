package jobs;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import models.RecordChangeMsg;
import org.apache.commons.lang.StringUtils;
import play.Play;
import play.classloading.ApplicationClasses;
import play.jobs.Job;
import play.jobs.JobsPlugin;
import play.jobs.OnApplicationStart;
import play.libs.F;
import play.modules.redis.Redis;
import play.modules.redis.RedisConnectionManager;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import util.CommonUtils;
import util.F10ScheduleSysConst;
import util.MsgChangeRedis;
import util.RedisKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Set;

import static util.F10ScheduleSysConst.HandleResult;
import static util.F10ScheduleSysConst.QUEUE_KEY_NAME;

/**
 * 主调度任务
 * 从redis上取消息, 根据消息生成别的任务任务进行调用
 * User: wenzhihong
 * Date: 12-9-28
 * Time: 上午8:47
 */
@OnApplicationStart(async = true)
public class MainSchedulJob extends Job {

    /**
     * 用于放置表名跟处理器的对应关系.
     */
    static SetMultimap<String, Class<Job>> tableHandlerMaps = LinkedHashMultimap.create();

    /**
     * 主任务的job的日志
     */
    public static org.apache.log4j.Logger log4j = org.apache.log4j.Logger.getLogger(MainSchedulJob.class);

    @Override
    public void doJob() throws Exception {
        boolean start = Boolean.parseBoolean(Play.configuration.getProperty("startMainSchedulJob", "false"));
        if(!start){
            return ;
        }

        initHandler();
        Gson gson = CommonUtils.createGson();
        MsgChangeRedis.init();
        Jedis jedisSource = null; //消息源头
        boolean jedisHasException = false;//标记jedis是否有异常
        while (true) {
            try {
                jedisHasException = false;
                jedisSource = MsgChangeRedis.getConnection();
                long queueLen = jedisSource.llen(QUEUE_KEY_NAME);
                int poolSize = JobsPlugin.executor.getPoolSize(); //可处理的线程
                int activeCount = JobsPlugin.executor.getActiveCount(); //当前正在处理的
                int jobQueueSize = JobsPlugin.executor.getQueue().size();//任务长度

                if (queueLen < 1) {
                    jedisSource.incr(F10ScheduleSysConst.REST_COUNT);
                    Thread.sleep(2 * 1000);
                } else if (jobQueueSize > 30) {
                    jedisSource.incr(F10ScheduleSysConst.REST_COUNT);
                    Thread.sleep(2 * 1000);
                } else {
                    String msg = jedisSource.lpop(QUEUE_KEY_NAME);
                    RecordChangeMsg recordChangeInfo = gson.fromJson(msg, RecordChangeMsg.class);

                    log4j.info(CommonUtils.format("消息:表名[%s] 动作[%s] utid[%d] 时间[%d]", recordChangeInfo.table, recordChangeInfo.action, recordChangeInfo.tid, recordChangeInfo.time));

                    if (recordChangeInfo.table != null) {
                        recordChangeInfo.table = recordChangeInfo.table.toLowerCase();
                    }

                    backupRecordChangeInfo(recordChangeInfo, gson);
                    dispatchHandler(recordChangeInfo, gson, jedisSource);
                }
            } catch (JedisConnectionException e) {
                jedisHasException = true;
                CommonUtils.warnLogWithStack(e, "redis连接异常(JedisConnectionException), 可能是服务器关闭,重新连接.%s", e.getMessage());
                Thread.sleep(2 * 1000);
            } catch (Exception e) {
                CommonUtils.warnLogWithStack(e, "主调度任务发生异常:%s休息一下", e.getMessage());
                Thread.sleep(5 * 1000);
            } finally {
                if (jedisHasException) {
                    MsgChangeRedis.closeBrokenConnection(jedisSource);
                } else {
                    MsgChangeRedis.closeConnection(jedisSource);
                }

                //这里要写上让他释放一下连接(playframework的redis module 的, 不然会一直只用一个连接. 过了30分钟, redis服务器会把它给断开)
                RedisConnectionManager.closeConnection();
            }
        }
    }

    /**
     * 备份消息到另外一个队列里去, 可能别人还要处理. 目前有建索引的要用
     */
    private void backupRecordChangeInfo(RecordChangeMsg recordChangeInfo, Gson gson) {
        if (StringUtils.isBlank(recordChangeInfo.handlerClassName)) { //没有处理过的, 说明是新的消息
            //处理如果消息太多的情况
            String key = RedisKey.Global.backup_record_change_list;
            Long len = Redis.llen(key);
            /*if (len > 10000000) { //大于1000w, 则取后面的一半来处理
                Redis.ltrim(key, len / 2, len - 1);
            }*/

            String msgStr = gson.toJson(recordChangeInfo);
            Redis.rpush(key, msgStr);
        }
    }

    /**
     * 分配到处理器进行处理
     *
     * @param recordChangeInfo
     * @throws InterruptedException
     */
    private void dispatchHandler(RecordChangeMsg recordChangeInfo, Gson gson, Jedis jedisSource) throws InterruptedException {
        jedisSource.incr(F10ScheduleSysConst.PROCESS_MSG_COUNT);
        Set<Class<Job>> handlerSet = null;
        if(StringUtils.isBlank(recordChangeInfo.handlerClassName)){ //没有处理过
            handlerSet = tableHandlerMaps.get(recordChangeInfo.table);
        }else{ //已经处理过了. 则直接就包含了处理器内容
            Class<Job> cl = null;
            try {
                cl = (Class<Job>) Class.forName(recordChangeInfo.handlerClassName);
            } catch (ClassNotFoundException e) {
                log4j.error(CommonUtils.format("加载处理器类(%s)不存在, 直接跳过..", recordChangeInfo.handlerClassName));
                return ;
            } catch (ClassCastException e){
                log4j.error(CommonUtils.format("加载处理器类(%s)不是Job类型, 直接跳过..", recordChangeInfo.handlerClassName));
                return ;
            }

            log4j.info("处理相关数据没有到的表的消息:" + recordChangeInfo);
            handlerSet = Sets.newHashSet(cl);
        }

        if (handlerSet.isEmpty()) {
            log4j.warn(CommonUtils.format("没有处理器处理表:%s", recordChangeInfo.table));
            return ;
        }

        for (Class<Job> jobClass : handlerSet) {
            RecordChangeMsg handMsg = recordChangeInfo.copyBaseInfo(); //处理的消息对象
            //先保存起来
            handMsg.handlerClassName = jobClass.getName();

            //接下来这里处理任务
            F.Promise<HandleResult> handlePromise = null;
            Job job = null;
            try {
                Constructor<Job> constructor = jobClass.getConstructor(RecordChangeMsg.class);
                job = constructor.newInstance(recordChangeInfo);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            } catch (InstantiationException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            if (job != null) {
                if (log4j.isDebugEnabled()) {
                    log4j.debug(CommonUtils.format("处理器[%s]处理表[%s]动作[%s]utsid[%d]", jobClass.getName(), recordChangeInfo.table, recordChangeInfo.action, recordChangeInfo.tid));
                }

                handlePromise = job.now();

                //这里处理任务结果
                if (handlePromise != null) {
                    try {
                        switch (handlePromise.get()) {
                            case NO_RECORD:
                                jedisSource.rpush(F10ScheduleSysConst.NO_RELEVANCE_QUEUE_NAME, gson.toJson(handMsg));
                                log4j.info("处理消息失败(可能是相关联的表的数据还没有过来),消息已保存把jedis上");
                                break;

                            case ERROR:
                                jedisSource.rpush(F10ScheduleSysConst.ERROR_QUEUE_NAME, gson.toJson(handMsg));
                                log4j.error("处理消息失败,消息已保存到redis上");
                                break;

                            case OK:
                                jedisSource.incr(F10ScheduleSysConst.SUCESS_COUNT);
                                break;

                            case NO_HANDLE:
                                log4j.warn(CommonUtils.format("没有相应表的处理器:%s", handMsg.table));
                                break;
                        }
                    } catch (Exception e) {
                        jedisSource.rpush(F10ScheduleSysConst.EXEC_EXCEPTION_QUEUE_NAME, gson.toJson(handMsg));
                        log4j.error("处理消息出现异常,异常信息", e);
                    }
                }
            }
        } //end for
    }

    /**
     * 初始化处理器
     */
    private void initHandler() {
        //找到 ProcessTable 的注解, 加入到 tableHandlerMaps 里
        List<ApplicationClasses.ApplicationClass> handlerClassList = Play.classes.getAnnotatedClasses(ProcessTable.class);
        for (ApplicationClasses.ApplicationClass handler : handlerClassList) {
            Class handlerJavaClass = handler.javaClass;
            if( handlerJavaClass != null && Job.class.isAssignableFrom(handlerJavaClass)) {
                ProcessTable annotation = (ProcessTable) handlerJavaClass.getAnnotation(ProcessTable.class);
                for (String tableName : annotation.value()) {
                    tableHandlerMaps.put(tableName.toLowerCase(), handlerJavaClass);
                }
            }

        }
    }


}
