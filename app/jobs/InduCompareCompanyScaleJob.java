package jobs;

import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.industryana.CompanyScale;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import play.modules.redis.Redis;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.List;
import java.util.Set;

/**
 * 处理 行业分析-公司规模
 * User: liuhongjiang
 * Date: 12-11-1
 * Time: 下午3:36
 */
@On("cron.InduCompareCompanyScaleTrigger")
@JobDesc(desc = "行业分析-公司规模")
public class InduCompareCompanyScaleJob extends Job {
      private static final String[] redisKeys = {RedisKey.IndustryAna.companyscale,RedisKey.IndustryAna.company_sec_scale};
    public void doJob() {
        RedisUtil.clean(redisKeys,"行业分析-公司规模");
        StopWatch sw = new StopWatch("行业分析-公司规模");
        sw.start();
        Gson gson = CommonUtils.createGson();
        String sql = SqlLoader.getSqlById("companyScaleRank");
        //要以行业代码作为参数
        for (String induCode : BondSec.fetchLevelTwoInduCodeList()) { //证监会行业二级代码
            List<CompanyScale> companyScaleList = ExtractDbUtil.queryExtractDBBeanList(sql, CompanyScale.class, induCode);

            List<CompanyScale> pre6CompanyScaleList;
            if (companyScaleList != null) {
                if (companyScaleList.size() > 6) {
                    pre6CompanyScaleList = companyScaleList.subList(0, 6);
                } else {
                    pre6CompanyScaleList = companyScaleList;
                }

                RedisUtil.set(RedisKey.IndustryAna.companyscale + induCode, pre6CompanyScaleList, gson);

                for (CompanyScale item : companyScaleList) {
                    RedisUtil.set(RedisKey.IndustryAna.company_sec_scale + item.institutionId, item, gson);
                }

            }
        }
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

}
