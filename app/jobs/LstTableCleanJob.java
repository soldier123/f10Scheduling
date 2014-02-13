package jobs;

import com.tom.springutil.StopWatch;
import dbutils.CustomDbUtil;
import org.apache.commons.dbutils.handlers.ScalarHandler;
import play.Logger;
import play.Play;
import play.jobs.Every;
import play.jobs.Job;
import util.CommonUtils;

import java.util.Date;

/**
 * User: wenzhihong
 * Date: 13-1-30
 * Time: 下午2:51
 */
@Every(value = "10min")
@JobDesc(desc = "每10分钟清理一下记录最新资讯的表")
public class LstTableCleanJob extends Job {
    static String[] cleanTable = {"c_ann_announcementinfo_lst","c_ann_classify_lst","c_ann_security_lst","c_ann_summaryinfo_lst","c_news_accessory_lst","c_news_classify_lst","c_news_industry_lst","c_news_newsinfo_lst","c_news_security_lst","c_rep_category_lst","c_rep_industry_lst","c_rep_institution_lst","c_rep_person_lst","c_rep_reportinfo_lst","c_rep_security_lst"};

    //更新declareDate的语句
    static String[] updateDeclareDateSql = {
            "UPDATE c_ann_classify_lst AS a, c_ann_announcementinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.ANNOUNCEMENTID = b.ANNOUNCEMENTID AND a.DECLAREDATE IS NULL",
            "UPDATE c_ann_security_lst AS a, c_ann_announcementinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.ANNOUNCEMENTID = b.ANNOUNCEMENTID AND a.DECLAREDATE IS NULL",
            "UPDATE c_news_accessory_lst AS a, c_news_newsinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.NEWSID = b.NEWSID AND a.DECLAREDATE IS NULL",
            "UPDATE c_news_classify_lst AS a, c_news_newsinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.NEWSID = b.NEWSID AND a.DECLAREDATE IS NULL",
            "UPDATE c_news_industry_lst AS a, c_news_newsinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.NEWSID = b.NEWSID AND a.DECLAREDATE IS NULL",
            "UPDATE c_news_security_lst AS a, c_news_newsinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.NEWSID = b.NEWSID AND a.DECLAREDATE IS NULL",
            "UPDATE c_rep_category_lst AS a, c_rep_reportinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.REPORTID = b.REPORTID AND a.DECLAREDATE IS NULL",
            "UPDATE c_rep_industry_lst AS a, c_rep_reportinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.REPORTID = b.REPORTID AND a.DECLAREDATE IS NULL",
            "UPDATE c_rep_institution_lst AS a, c_rep_reportinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.REPORTID = b.REPORTID AND a.DECLAREDATE IS NULL",
            "UPDATE c_rep_person_lst AS a, c_rep_reportinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.REPORTID = b.REPORTID AND a.DECLAREDATE IS NULL",
            "UPDATE c_rep_security_lst AS a, c_rep_reportinfo_lst AS b SET a.DECLAREDATE = b.DECLAREDATE WHERE a.REPORTID = b.REPORTID AND a.DECLAREDATE IS NULL",
    };

    @Override
    public void doJob() throws Exception {
        //如果主调度有运行的话, 则也要进行清小表操作
        boolean start = Boolean.parseBoolean(Play.configuration.getProperty("startMainSchedulJob", "false"));
        if(!start){
            return ;
        }

        StopWatch sw = new StopWatch("清理资讯小表");
        sw.start("处理declareDate");
        for(String s : updateDeclareDateSql){
            CustomDbUtil.updateCustomDB(s);
        }
        sw.stop();

        for (String t : cleanTable) {
            sw.start("处理表:" + t);
            String maxDateSql = String.format("select max(declaredate) from %s", t);
            Date maxDate = CustomDbUtil.queryCustomDbWithHandler(maxDateSql, new ScalarHandler<Date>());
            if(maxDate != null){
                String dateStr = CommonUtils.getFormatDate("yyyy-MM-dd", maxDate);

                String crDelSql = String.format("DELETE FROM %s WHERE create_time < DATE_ADD('%s', INTERVAL -1 MONTH) and DECLAREDATE is null", t, dateStr);
                CustomDbUtil.updateCustomDB(crDelSql);

                String delSql = String.format("DELETE FROM %s WHERE declareDate < DATE_ADD('%s', INTERVAL -3 MONTH)", t, dateStr);
                CustomDbUtil.updateCustomDB(delSql);
            }
            sw.stop();
        }
        if (Logger.isDebugEnabled()) {
            Logger.debug(sw.prettyPrint());
        }
    }
}
