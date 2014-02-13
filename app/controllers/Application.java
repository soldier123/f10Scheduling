package controllers;

import com.google.common.collect.Lists;
import play.modules.redis.Redis;
import play.mvc.Controller;
import redis.clients.jedis.Jedis;
import util.F10ScheduleSysConst;
import util.MsgChangeRedis;
import util.RedisKey;

import java.util.List;

public class Application extends Controller {

    public static void index() {
        render();
    }

    /**
     * 队列信息
     */
    public static void queueInfo() {
        render();
    }

    /**
     * 清空mysql  数据变化的队列信息
     */
    public static void cleanMysqlChangeCount() {
        Jedis jedisSource = null;
        try {
            jedisSource = MsgChangeRedis.getConnection();
            jedisSource.del(new String[]{F10ScheduleSysConst.TOTAL_INSERT_NUM_KEY_NAME, F10ScheduleSysConst.TOTAL_UPDATE_NUM_KEY_NAME, F10ScheduleSysConst.TOTAL_DEL_NUM_KEY_NAME});
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
            renderText("清空计数器成功");
        }
    }

    public static void cleanMysqlChangeQueue() {
        Jedis jedisSource = null;
        try {
            jedisSource = MsgChangeRedis.getConnection();
            jedisSource.del(new String[]{F10ScheduleSysConst.QUEUE_KEY_NAME});
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
            renderText("清空队列成功");
        }
    }


    /**
     * mysql 数据变化的队列情况
     */
    public static void mysqlChangeRedisInfo() {
        Jedis jedisSource = null;
        try {
            jedisSource = MsgChangeRedis.getConnection();
            Long queueLen = jedisSource.llen(F10ScheduleSysConst.QUEUE_KEY_NAME);
            String insertCount = jedisSource.get(F10ScheduleSysConst.TOTAL_INSERT_NUM_KEY_NAME);
            String updateCount = jedisSource.get(F10ScheduleSysConst.TOTAL_UPDATE_NUM_KEY_NAME);
            String delCount = jedisSource.get(F10ScheduleSysConst.TOTAL_DEL_NUM_KEY_NAME);
            render(queueLen, insertCount, updateCount, delCount);
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
        }
    }

    /**
     * 清空job计数
     */
    public static void cleanJobCount() {
        Jedis jedisSource = null;
        try {
            jedisSource = MsgChangeRedis.getConnection();
            jedisSource.del(new String[]{F10ScheduleSysConst.REST_COUNT, F10ScheduleSysConst.SUCESS_COUNT, F10ScheduleSysConst.PROCESS_MSG_COUNT});
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
            renderText("清空job计数器");
        }
    }

    /**
     * 清空job队列
     */
    public static void cleanJobQueue(String queueName) {
        if ("backup_change_msg_queue".equals(queueName)) {
            Redis.del(new String[]{RedisKey.Global.backup_record_change_list});
            renderText("清空备份消息队列");
        } else {
            Jedis jedisSource = null;
            try {
                jedisSource = MsgChangeRedis.getConnection();
                jedisSource.del(new String[]{queueName});
            } finally {
                MsgChangeRedis.closeConnection(jedisSource);
                renderText("清空job队列:%s", queueName);
            }
        }
    }

    /**
     * 任务处理队列情况
     */
    public static void jobRedisInfo() {
        Jedis jedisSource = null;
        try {
            jedisSource = MsgChangeRedis.getConnection();
            Long noRelevanceLen = jedisSource.llen(F10ScheduleSysConst.NO_RELEVANCE_QUEUE_NAME);
            Long errorLen = jedisSource.llen(F10ScheduleSysConst.ERROR_QUEUE_NAME);
            Long execExceptionErrorLen = jedisSource.llen(F10ScheduleSysConst.EXEC_EXCEPTION_QUEUE_NAME);
            String restCount = jedisSource.get(F10ScheduleSysConst.REST_COUNT);
            String sucessCount = jedisSource.get(F10ScheduleSysConst.SUCESS_COUNT);
            String processMsgCount = jedisSource.get(F10ScheduleSysConst.PROCESS_MSG_COUNT);
            render(noRelevanceLen, errorLen, execExceptionErrorLen, restCount, sucessCount, processMsgCount);
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
        }
    }

    /**
     * 查看 队列的情况
     */
    public static void queueRangeInfo(String queueType, int start, int end) {
        List<String> resultList = null;
        Jedis jedisSource = null;
        try {
            jedisSource = MsgChangeRedis.getConnection();
            if ("change_msg_queue".equalsIgnoreCase(queueType)) {
                resultList = jedisSource.lrange(F10ScheduleSysConst.QUEUE_KEY_NAME, start, end);

            } else if ("job_no_relevance_queue".equalsIgnoreCase(queueType)) {
                resultList = jedisSource.lrange(F10ScheduleSysConst.NO_RELEVANCE_QUEUE_NAME, start, end);
            } else if ("job_error_queue".equalsIgnoreCase(queueType)) {
                resultList = jedisSource.lrange(F10ScheduleSysConst.ERROR_QUEUE_NAME, start, end);
            } else if ("job_exec_exception_queue".equalsIgnoreCase(queueType)) {
                resultList = jedisSource.lrange(F10ScheduleSysConst.EXEC_EXCEPTION_QUEUE_NAME, start, end);
            } else if("backup_change_msg_queue".equalsIgnoreCase(queueType)){
                resultList = Redis.lrange(RedisKey.Global.backup_record_change_list, start, end);
            } else {
                resultList = Lists.newArrayList();
            }
            render(resultList);
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
        }
    }

    /**
     * 消息转移
     * @param queueType 队列类型
     * @param queueLen 长度
     */
    public static void queueTrans(String queueType, long queueLen) {
        List<String> resultList = null;
        Jedis jedisSource = null;
        String result = "";
        try{
            jedisSource = MsgChangeRedis.getConnection();
            String queueKeyName = null;
            if ("job_no_relevance_queue".equalsIgnoreCase(queueType)) {
                queueKeyName = F10ScheduleSysConst.NO_RELEVANCE_QUEUE_NAME;
            }else if("job_exec_exception_queue".equalsIgnoreCase(queueType)){
                queueKeyName = F10ScheduleSysConst.EXEC_EXCEPTION_QUEUE_NAME;
            }else if ("job_error_queue".equalsIgnoreCase(queueType)) {
                queueKeyName = F10ScheduleSysConst.EXEC_EXCEPTION_QUEUE_NAME;
            }

            if (queueKeyName != null) {
                resultList = jedisSource.lrange(queueKeyName, 0, queueLen);
                if (resultList != null && resultList.size() > 0) {
                    //放入到新队列
                    for (String s : resultList) {
                        jedisSource.rpush(F10ScheduleSysConst.QUEUE_KEY_NAME, s);
                    }

                    //清除原先队列里的内容
                    long len = jedisSource.llen(queueKeyName);
                    if (queueLen < 0) {
                        queueLen = len + queueLen;
                        if (queueLen < 0) {
                            queueLen = len;
                        }
                    }
                    jedisSource.ltrim(queueKeyName, queueLen + 1, -1);
                }
                result = "成功将消息迁移";
            }else {
                result = "没有传队列参数类型";
            }
        } catch (Exception e){
            result = "处理失败";
        } finally {
            MsgChangeRedis.closeConnection(jedisSource);
            renderText(result);
        }
    }

    public static void backupQueueInfo() {
        Long queueLen = Redis.llen(RedisKey.Global.backup_record_change_list);
        render(queueLen);
    }
}