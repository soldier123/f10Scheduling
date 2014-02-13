package jobs;

import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.BeanMultiMapHandler;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.financeana.*;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.Date;
import java.util.List;

/**
 *  处理 财务分析- 主要财务指标.
 *  财务简表-资产负债表.
 *  财务简表-现金流量表
 *  财务简表-利润表
 * User: wenzhihong
 * Date: 12-12-21
 * Time: 下午2:06
 */
@On("cron.FinanceItemTrigger")
@JobDesc(desc = "财务分析- 主要财务指标 及三大财务报表")
public class FinanceItemJob extends Job{
    private static final String[] redisKeys = {
            RedisKey.FinanceAna.debtPay,
            RedisKey.FinanceAna.earnPower,
            RedisKey.FinanceAna.perShare,
            RedisKey.FinanceAna.lcDiscloseIndex,
            RedisKey.FinanceAna.balanceSheet,
            RedisKey.FinanceAna.cashFlowSheet,
            RedisKey.FinanceAna.cashFlowSheetSingle,
            RedisKey.FinanceAna.incomeSheet,
            RedisKey.FinanceAna.incomeSheetSingle,
            RedisKey.FinanceAna.assetImpairment,
            RedisKey.FinanceAna.equityInvest,
            RedisKey.FinanceAna.longtermPrepaidFee,
            RedisKey.FinanceAna.deferredIncomeTax,
            RedisKey.FinanceAna.operateIncomeCosts,
            RedisKey.FinanceAna.financeCosts,
            RedisKey.FinanceAna.businessTaxAppend
    };
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"财务分析- 财务全景图");
        StopWatch sw = new StopWatch("财务分析- 主要财务指标");

        sw.start("debtPay");
        fetchData("debtPaySql", RedisKey.FinanceAna.debtPay, DebtPayItem.class);
        sw.stop();

        sw.start("earnPower");
        fetchData("earnPowerSql", RedisKey.FinanceAna.earnPower, EarnPowerItem.class);
        sw.stop();

        sw.start("perShare");
        fetchData("perShareSql", RedisKey.FinanceAna.perShare, PerShareItem.class);
        sw.stop();

        sw.start("lcDiscloseIndex");
        fetchData("lcDiscloseIndexSql", RedisKey.FinanceAna.lcDiscloseIndex, LcDiscloseIndexItem.class);
        sw.stop();

        sw.start("财务简表-资产负债表");
        fetchData("financeBalance40", RedisKey.FinanceAna.balanceSheet, BalanceSheet.class);
        sw.stop();

        sw.start("财务简表-现金流量表");
        fetchData("financeCashFlow40", RedisKey.FinanceAna.cashFlowSheet, CashFlowSheet.class);
        sw.stop();

        sw.start("财务简表-现金流量表 单季度");
        fetchData("financeCashFlow40SingleSeason", RedisKey.FinanceAna.cashFlowSheetSingle, CashFlowSheet.class);
        sw.stop();

        sw.start("财务简表-利润表");
        fetchData("financeIncome40", RedisKey.FinanceAna.incomeSheet, IncomeSheet.class);
        sw.stop();

        sw.start("财务简表-利润表 单季度");
        fetchData("financeIncome40SingleSeason", RedisKey.FinanceAna.incomeSheetSingle, IncomeSheet.class);
        sw.stop();

        sw.start("资产负债表附注—资产减值准备");
        fetchData("assetImpairmentSql", RedisKey.FinanceAna.assetImpairment, AssetImpairment.class);
        sw.stop();

        sw.start("资产负债表附注—长期股权投资");
        fetchData("equityInvestSql", RedisKey.FinanceAna.equityInvest, EquityInvest.class);
        sw.stop();

        sw.start("资产负债表附注—长期待摊费用");
        fetchData("longtermPrepaidFeeSql", RedisKey.FinanceAna.longtermPrepaidFee, LongtermPrepaidFee.class);
        sw.stop();

        sw.start("资产负债表附注—递延所得税资产和递延所得税负债");
        fetchData("deferredIncomeTaxSql", RedisKey.FinanceAna.deferredIncomeTax, DeferredIncomeTax.class);
        sw.stop();

        sw.start("利润表附注—营业收入、营业成本");
        fetchData("operateIncomeCostsSql", RedisKey.FinanceAna.operateIncomeCosts, OperateIncomeCosts.class);
        sw.stop();

        sw.start("利润表附注—财务费用");
        fetchData("financeCostsSql", RedisKey.FinanceAna.financeCosts, FinanceCosts.class);
        sw.stop();

        sw.start("利润表附注—营业税金及附加");
        fetchData("businessTaxAppendSql", RedisKey.FinanceAna.businessTaxAppend, BusinessTaxAppend.class);
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    private <T> void  fetchData(String sqlId, String redisKey, Class<T> tclass){
        Gson gson = CommonUtils.createGson();
        String sqlTmp = SqlLoader.getSqlById(sqlId);
        String minEndDate = CommonUtils.calcReportDate(new Date(), -40); //向前推40期
        minEndDate = minEndDate.substring(0, 4) + "-01-01";

        for (String orgIdGroup : BondSec.institutionIdGroupArr) { //处理一组股票
            String sql = sqlTmp.replaceAll("#orgIdGroup#", orgIdGroup);
            ListMultimap<Number, T> listMultimap = ExtractDbUtil.queryExtractDbWithHandler(sql,
                    new BeanMultiMapHandler<Number, T>(tclass, "institutionId"), minEndDate);

            for (Number institutionId : listMultimap.keySet()) { //处理每一支股票
                List<T> list = Lists.newArrayList(listMultimap.get(institutionId));
                RedisUtil.setGsonWithCompress(redisKey + institutionId.longValue(), list, gson);
            }
        }
    }
}
