package models;

/**
 * 数据库记录变化消息. 从redis上获取
 * User: wenzhihong
 * Date: 12-9-28
 * Time: 上午8:52
 */
public class RecordChangeMsg {
    //表更新的记录id utsid
    public long tid;

    public String schema; //数据库名

    public String table;

    public String action;

    //时间, 这里用long 型表示. 要转成java的Date类型. 直接用 new Date(long d) 从1970.1.1 00:00:00 GMT
    public long time;

    //处理器名称
    public String handlerClassName;

    //失败次数
    public int failCount = 0;

    /**
     * 拷贝生成一个新的对象. 除 处理器名称(handlerClassName) 属性
     */
    public RecordChangeMsg copyBaseInfo(){
        RecordChangeMsg obj = new RecordChangeMsg();
        obj.tid = this.tid;
        obj.table = this.table;
        obj.action = this.action;
        obj.time = this.time;

        return obj;
    }

    @Override
    public String toString() {
        return "RecordChangeMsg{" +
                "tid=" + tid +
                ", schema='" + schema + '\'' +
                ", table='" + table + '\'' +
                ", action='" + action + '\'' +
                ", time=" + time +
                ", handlerClassName='" + handlerClassName + '\'' +
                ", failCount=" + failCount +
                '}';
    }
}
