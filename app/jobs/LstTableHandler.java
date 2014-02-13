package jobs;

import dbutils.CustomDbUtil;
import dbutils.ExtractDbUtil;
import models.LstTableInfo;
import models.RecordChangeMsg;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import play.Logger;
import play.exceptions.DatabaseException;
import util.F10ScheduleSysConst;

import java.sql.SQLException;
import java.util.Date;
import java.util.Map;

/**
 * User: wenzhihong
 * Date: 13-1-30
 * Time: 上午10:51
 */
@ProcessTable({"ann_announcementinfo","ann_classify","ann_security","ann_summaryinfo",
"news_accessory","news_classify","news_industry","news_newsinfo","news_security",
"rep_category","rep_industry","rep_institution","rep_person","rep_reportinfo","rep_security"})
public class LstTableHandler extends AbstractBusinessHandler {

    public LstTableHandler(RecordChangeMsg recordChangeInfo) {
        super(recordChangeInfo);
    }

    @Override
    protected F10ScheduleSysConst.HandleResult processDelAction() {
        String tableName = recordChangeInfo.table;
        LstTableInfo tableInfo = LstTableInfo.tmap.get(tableName);
        if (tableInfo != null) {
            if (Logger.isDebugEnabled()) {
                Logger.debug("处理del记录");
            }
            CustomDbUtil.updateCustomDB(tableInfo.delSql, recordChangeInfo.tid);
        } else {
            Logger.warn("没有找到处理(最新数据表): %s的配制信息, 跳过");
        }
        return F10ScheduleSysConst.HandleResult.OK;
    }

    @Override
    protected F10ScheduleSysConst.HandleResult processUpdateAction() {
        String tableName = recordChangeInfo.table;
        LstTableInfo tableInfo = LstTableInfo.tmap.get(tableName);
        if (tableInfo != null) {
            Map<String,Object> record = ExtractDbUtil.queryExtractDBSingleMap(tableInfo.selectSrcTableSql, recordChangeInfo.tid); //提取原始记录
            if (record != null) {
                //修改
                Object[] params  = new Object[record.size() + 1];
                int i = 0;
                for (String f : tableInfo.fields) {
                    params[i] = record.get(f);
                    i++;
                }
                params[record.size()] = recordChangeInfo.tid;
                if (Logger.isDebugEnabled()) {
                    Logger.debug("处理update记录");
                }
                CustomDbUtil.updateCustomDB(tableInfo.updateSql, params);
            }
        }else{
            Logger.warn("没有找到处理(最新数据表): %s的配制信息, 跳过");
        }

        return F10ScheduleSysConst.HandleResult.OK;
    }

    @Override
    protected F10ScheduleSysConst.HandleResult processInsertAction() {
        boolean debugEnabled = Logger.isDebugEnabled();
        String tableName = recordChangeInfo.table;
        LstTableInfo tableInfo = LstTableInfo.tmap.get(tableName);
        if(tableInfo != null){
            if("_self".equalsIgnoreCase(tableInfo.mainTalbe)){ //主表
                Map<String,Object> record = ExtractDbUtil.queryExtractDBSingleMap(tableInfo.selectSrcTableSql, recordChangeInfo.tid); //提取原始记录
                if (record != null) {
                    //插入主表
                    Object[] params  = new Object[record.size() + 1];
                    int i = 0;
                    for (String f : tableInfo.fields) {
                        params[i] = record.get(f);
                        i++;
                    }
                    params[record.size()] = recordChangeInfo.tid;
                    if (debugEnabled) {
                        Logger.debug("插入主表记录");
                    }

                    try{
                        CustomDbUtil.updateCustomDB(tableInfo.insertSql, params);
                    }catch (DatabaseException e){
                        insertErrorCode(e);
                        return F10ScheduleSysConst.HandleResult.OK;
                    }

                    //更新子表
                    Object declaredate = record.get("DECLAREDATE");
                    if (debugEnabled) {
                        Logger.debug("更新子表declareDate列");
                    }

                    for (LstTableInfo childTable : tableInfo.childTable) {
                        Object joinColVal = record.get(childTable.joinColumn);
                        CustomDbUtil.updateCustomDB(childTable.subTableUpdateDeclareDateSql, declaredate, joinColVal);
                    }
                }
            }else{ //都认为是子表
                Map<String,Object> record = ExtractDbUtil.queryExtractDBSingleMap(tableInfo.selectSrcTableSql, recordChangeInfo.tid); //提取原始记录
                if(record != null){
                    Object joinColVal = record.get(tableInfo.joinColumn); //连接字段的值
                    if(joinColVal != null){
                        Date declareDate = CustomDbUtil.queryCustomDbWithHandler(tableInfo.selectMainTableDeclareDateSql, new ScalarHandler<Date>(), joinColVal);
                         //子表插入
                        Object[] params  = new Object[record.size() + 1];
                        int i = 0;
                        for (String f : tableInfo.fields) {
                            params[i] = record.get(f);
                            i++;
                        }
                        params[record.size()] = recordChangeInfo.tid;
                        if (debugEnabled) {
                            Logger.debug("插入子表记录");
                        }
                        try{
                            CustomDbUtil.updateCustomDB(tableInfo.insertSql, params);
                        }catch (DatabaseException e){
                            insertErrorCode(e);
                            return F10ScheduleSysConst.HandleResult.OK;
                        }

                        if(declareDate != null){ //更新子表的 declareDate 列
                            if (debugEnabled) {
                                Logger.debug("更新子表declareDate列");
                            }
                            CustomDbUtil.updateCustomDB(tableInfo.subTableUpdateDeclareDateSql, declareDate, joinColVal);
                        }
                    }
                }
            }
        }else{
            Logger.warn("没有找到处理(最新数据表): %s的配制信息, 跳过");
        }

        return F10ScheduleSysConst.HandleResult.OK;
    }

    private void insertErrorCode(DatabaseException e) {
        SQLException se = (SQLException) e.getCause();
        if(se.getErrorCode() == 1062){ //主键重复  这里只针对mysql.
            Logger.error("处理消息%s,出现主键重复:%s", recordChangeInfo.toString(), se.getCause());
        }
    }
}
