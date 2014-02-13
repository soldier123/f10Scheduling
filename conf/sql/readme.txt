这里存放的是完整的sql语句.
有两种方式.

1. .sql文件, 只能有一个sql. 以sql文件的名字(去掉.sql)做为sqlid

2. .xml文件, 这里的xml文件是按 http://java.sun.com/dtd/properties.dtd 格式的, 这里用到的是jdk里的Properties.loadFromXML方法的支持

3. 在程序调用时, 使用SqlLoader.getSqlById(String sqlId)  得到sql语句