package jobs;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.primitives.Doubles;
import com.google.gson.Gson;
import com.tom.springutil.StopWatch;
import dbutils.ExtractDbUtil;
import dbutils.SqlLoader;
import dto.BondSec;
import dto.financeana.FullView;
import dto.financeana.FullViewItem;
import play.Logger;
import play.jobs.Job;
import play.jobs.On;
import util.CommonUtils;
import util.RedisKey;
import util.RedisUtil;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 财务分析- 财务全景图
 * User: wenzhihong
 * Date: 12-12-22
 * Time: 上午11:31
 */
@On("cron.FinanceFullViewTrigger")
@JobDesc(desc = "财务分析- 财务全景图")
public class FinanceFullViewJob extends Job {
  private static final String[] redisKeys = {RedisKey.FinanceAna.fullView,};
    @Override
    public void doJob() throws Exception {
        RedisUtil.clean(redisKeys,"财务分析- 财务全景图");
        Map<Long, FullView> fullViewMap = Maps.newHashMap();

        StopWatch sw = new StopWatch("财务分析-财务全景图");
        {
            //短期偿债能力
            sw.start("短期偿债能力");
            List<FullViewItem> items = calcData("shortDebtRepayment");
            for (FullViewItem it : items) {
                FullView f = fullViewMap.get(it.institutionId);
                if (f == null) {
                    f = new FullView();
                    fullViewMap.put(it.institutionId, f);
                }
                f.shortDebtRepaymentDate = it.enddateStr;
                f.shortDebtRepaymentStep = CommonUtils.scaleNum(it.stepVal, 0, "#.00");
            }
            sw.stop();
        }
        {
            //长期偿债能力
            sw.start("长期偿债能力");
            List<FullViewItem> items = calcData("longDebtRepayment");
            for (FullViewItem it : items) {
                FullView f = fullViewMap.get(it.institutionId);
                if (f == null) {
                    f = new FullView();
                    fullViewMap.put(it.institutionId, f);
                }
                f.longDebtRepaymentDate = it.enddateStr;
                f.longDebtRepaymentStep = CommonUtils.scaleNum(it.stepVal, 0, "#.00");
            }
            sw.stop();
        }
        {
            //经营能力
            sw.start("经营能力");
            List<FullViewItem> items = calcData("operateCapacity");
            for (FullViewItem it : items) {
                FullView f = fullViewMap.get(it.institutionId);
                if (f == null) {
                    f = new FullView();
                    fullViewMap.put(it.institutionId, f);
                }
                f.operateCapacityDate = it.enddateStr;
                f.operateCapacityStep = CommonUtils.scaleNum(it.stepVal, 0, "#.00");
            }
            sw.stop();
        }
        {
            //盈利能力
            sw.start("盈利能力");
            List<FullViewItem> items = calcData("earnPowerCapacity");
            for (FullViewItem it : items) {
                FullView f = fullViewMap.get(it.institutionId);
                if (f == null) {
                    f = new FullView();
                    fullViewMap.put(it.institutionId, f);
                }
                f.earnPowerCapacityDate = it.enddateStr;
                f.earnPowerCapacityStep = CommonUtils.scaleNum(it.stepVal, 0, "#.00");
            }
            sw.stop();
        }
        {
            //投资收益
            sw.start("投资收益");
            List<FullViewItem> items = calcData("roiCapacity");
            for (FullViewItem it : items) {
                FullView f = fullViewMap.get(it.institutionId);
                if (f == null) {
                    f = new FullView();
                    fullViewMap.put(it.institutionId, f);
                }
                f.roiCapacityDate = it.enddateStr;
                f.roiCapacityStep = CommonUtils.scaleNum(it.stepVal, 0, "#.00");
            }
            sw.stop();
        }
        {
            //成长性
            sw.start("成长性");
            List<FullViewItem> items = calcData("developmentCapacity");
            for (FullViewItem it : items) {
                FullView f = fullViewMap.get(it.institutionId);
                if (f == null) {
                    f = new FullView();
                    fullViewMap.put(it.institutionId, f);
                }
                f.developmentCapacityDate = it.enddateStr;
                f.developmentCapacityStep = CommonUtils.scaleNum(it.stepVal, 0, "#.00");
            }
            sw.stop();
        }

        sw.start("设置到redis上");
        Gson gson = CommonUtils.createGson();
        //设置到redis上
        for (Long institutionId : fullViewMap.keySet()) {
            RedisUtil.set(RedisKey.FinanceAna.fullView + institutionId, fullViewMap.get(institutionId), gson);
        }
        sw.stop();

        Logger.info(sw.prettyPrint());
    }

    private List<FullViewItem> calcData(String sqlId) {
        List<FullViewItem> items = Lists.newArrayListWithCapacity(BondSec.secMap.size());
        String sqlTml = SqlLoader.getSqlById(sqlId);
        for (String orgGroup : BondSec.institutionIdGroupArr) {
            List<FullViewItem> subItem = ExtractDbUtil.queryExtractDBBeanList(sqlTml.replaceAll("#orgGroup#", orgGroup), FullViewItem.class);
            if(subItem != null){
                items.addAll(subItem);
            }
        }

        if (items != null && items.size() > 0) {
            Collections.sort(items, new Comparator<FullViewItem>() {
                @Override
                public int compare(FullViewItem o1, FullViewItem o2) {
                    return Doubles.compare(o1.avgVal, o2.avgVal);
                }
            });

/*          这种是根据数值在计算的, 我觉得如果数值偏差很大的话, 这个值不合理
            double min = items.get(0).avgVal;
            double max = items.get(items.size() - 1).avgVal;
            double step = (max - min) / 5;
            if (step != 0) {
                for (FullViewItem item : items) {
                    item.stepVal = (item.avgVal - min) / step;
                }
            } else { //如果是这样的话, 都设置为5
                for (FullViewItem item : items) {
                    item.stepVal = 5;
                }
            }
*/

            if (items.size() < 5) { //如果小于5个,则用数值计算
                double min = items.get(0).avgVal;
                double max = items.get(items.size() - 1).avgVal;
                double step = (max - min) / 5;
                if (step != 0) {
                    for (FullViewItem item : items) {
                        item.stepVal = (item.avgVal - min) / step;
                    }
                } else { //如果是这样的话, 都设置为5
                    for (FullViewItem item : items) {
                        item.stepVal = 5;
                    }
                }
            } else {
                //采用排名来计算
                int index = 0;
                int step = items.size() / 5;
                for (FullViewItem item : items) {
                    item.stepVal = index * 1.0 / step;
                    index++;
                }
            }
            

        }
        return items;
    }


}
