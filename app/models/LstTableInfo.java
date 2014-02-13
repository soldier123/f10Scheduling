package models;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import play.Play;
import util.CommonUtils;

import java.util.List;
import java.util.Map;

/**
 * User: wenzhihong
 * Date: 13-1-29
 * Time: 下午5:57
 */
public class LstTableInfo {
    //表名
    public String tableName;

    //要插入更新的字段
    public String[]  fields;

    //主表
    public String  mainTalbe;

    //跟主表关联的字段
    public String  joinColumn;

    //子表
    public List<LstTableInfo> childTable = Lists.newArrayList();

    public static Map<String, LstTableInfo> tmap = Maps.newHashMap();

    //查询原始表sql
    public String selectSrcTableSql;

    //insert 语句
    public String insertSql;

    //更新语句
    public String updateSql;

    //删除语句
    public String delSql;

    //子表更新时间的sql
    public String subTableUpdateDeclareDateSql;

    //查出主表 declareDate 字段的sql语句
    public String selectMainTableDeclareDateSql;

    //初始化
    public static void init(){
        String configStr = CommonUtils.readJsonConfigFile2String(Play.getVirtualFile("conf/lst_table_info.js").inputstream());
        Gson gson = CommonUtils.createGson();
        List<LstTableInfo> list = gson.fromJson(configStr, new TypeToken<List<LstTableInfo>>(){}.getType());
        for (LstTableInfo parent : list) {//初始化父子关系
            for (LstTableInfo child : list) {
                if(parent.tableName.equals(child.mainTalbe)){
                    parent.childTable.add(child);
                }
            }
        }

        Joiner selectFieldJoin = Joiner.on(",");
        Joiner updateFieldJoin = Joiner.on(" = ?,");

        for (LstTableInfo t : list) {
            boolean subTable = ! "_self".equalsIgnoreCase(t.mainTalbe);
            String srcName = t.tableName;
            tmap.put(t.tableName, t);
            t.tableName = "c_" + t.tableName + "_lst";
            if(subTable){
                t.mainTalbe = "c_" + t.mainTalbe + "_lst";
                t.selectMainTableDeclareDateSql = String.format("select DECLAREDATE from %s where %s = ?", t.mainTalbe, t.joinColumn);
            }
            t.selectSrcTableSql = String.format("select %s from %s where utsid = ?", selectFieldJoin.join(t.fields), srcName);

            StringBuilder insertSb = new StringBuilder("insert into ").append(t.tableName).append("(");
            for (String f : t.fields) {
                insertSb.append(f).append(",");
            }
            insertSb.append("UTSID ) VALUES (");
            for (String f : t.fields) {
                insertSb.append("?,");
            }
            insertSb.append("?)");
            t.insertSql = insertSb.toString();

            t.updateSql = String.format("update %s set %s =? where utsid = ?", t.tableName, updateFieldJoin.join(t.fields));
            t.delSql = String.format("delete from %s where utsid = ?", t.tableName);

            if(subTable){ //子表
                t.subTableUpdateDeclareDateSql = String.format("update %s set declaredate = ? where %s = ?", t.tableName, t.joinColumn);
            }
        }
    }
}

