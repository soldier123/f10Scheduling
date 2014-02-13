/*---说明: 初始化资讯小表数据.*/
/*-------------------------------------------------------------*/
TRUNCATE TABLE qic_db.c_ann_announcementinfo_lst;

INSERT INTO qic_db.c_ann_announcementinfo_lst
            (ANNOUNCEMENTID,
             DECLAREDATE,
             TITLE,
             FILESIZE,
             ANNOUNCEMENTTYPE,
             ANNOUNCEMENTROUTE,
             UTSID)
SELECT
	ANNOUNCEMENTID,
	DECLAREDATE,
	TITLE,
	FILESIZE,
	ANNOUNCEMENTTYPE,
	ANNOUNCEMENTROUTE,
	UTSID
FROM gta_data.ann_announcementinfo
WHERE DECLAREDATE >= '2013-02-01';

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_ann_classify_lst;

INSERT INTO qic_db.c_ann_classify_lst
            (ANNOUNCEMENTID,
             CLASSIFYID,
             CLASSIFYNAME,
             DECLAREDATE,
             UTSID)

SELECT * FROM (
	SELECT 	a.ANNOUNCEMENTID,
			a.CLASSIFYID,
			a.CLASSIFYNAME,
			b.DECLAREDATE,
			a.UTSID
     FROM gta_Data.ann_classify a INNER JOIN gta_Data.ann_announcementinfo b
		ON a.ANNOUNCEMENTID = b.ANNOUNCEMENTID
	WHERE b.DECLAREDATE >= '2013-02-01'

) AS ddd;

/*-------------------------------------------------------------*/

TRUNCATE TABLE  qic_db.c_ann_security_lst;

INSERT INTO qic_db.c_ann_security_lst
            (ANNOUNCEMENTID,
             SECURITYID,
             SYMBOL,
             SECURITYTYPEID,
             SECURITYTYPE,
             DECLAREDATE,
             UTSID)

SELECT * FROM (
	SELECT
		a.ANNOUNCEMENTID,
		a.SECURITYID,
		a.SYMBOL,
		a.SECURITYTYPEID,
		a.SECURITYTYPE,
		b.DECLAREDATE,
		a.UTSID
     FROM gta_Data.ann_security a INNER JOIN gta_data.ann_announcementinfo b
     ON a.ANNOUNCEMENTID = b.ANNOUNCEMENTID
     WHERE b.DECLAREDATE >= '2013-02-01'

) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_ann_summaryinfo_lst;

INSERT INTO qic_db.c_ann_summaryinfo_lst
            (ANNOUNCEMENTID,
             DECLAREDATE,
             SUMMARYTITLE,
             SUMMARYCONTENT,
             SUMMARYSIZE,
             UTSID)

SELECT
	ANNOUNCEMENTID,
	DECLAREDATE,
	SUMMARYTITLE,
	SUMMARYCONTENT,
	SUMMARYSIZE,
	UTSID
FROM gta_data.ann_summaryinfo
WHERE   DECLAREDATE >= '2013-02-01';

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_news_accessory_lst;

INSERT INTO qic_db.c_news_accessory_lst
            (NEWSID,
             RANK,
             DECLAREDATE,
             FULLNAME,
             BRIEF,
             ACCESSORYTYPE,
             ACCESSORYROUTE,
             ACCESSORYSIZE,
             UTSID)

SELECT * FROM (

	SELECT
		a.NEWSID,
		a.RANK,
		b.DECLAREDATE,
		a.FULLNAME,
		a.BRIEF,
		a.ACCESSORYTYPE,
		a.ACCESSORYROUTE,
		a.ACCESSORYSIZE,
		a.UTSID
    FROM gta_data.news_accessory a INNER JOIN gta_data.news_newsinfo b
    ON a.NEWSID = b.NEWSID
    WHERE b.DECLAREDATE >= '2013-02-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_news_classify_lst;

INSERT INTO qic_db.c_news_classify_lst
            (NEWSID,
             CLASSIFYID,
             CLASSIFYNAME,
             DECLAREDATE,
             UTSID)

SELECT * FROM
(
	SELECT
		a.NEWSID,
		a.CLASSIFYID,
		a.CLASSIFYNAME,
		b.DECLAREDATE,
		a.UTSID
    FROM gta_data.news_classify a INNER JOIN gta_data.news_newsinfo b
    ON a.NEWSID = b.NEWSID
    WHERE b.DECLAREDATE >= '2013-02-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_news_industry_lst;

INSERT INTO qic_db.c_news_industry_lst
            (NEWSID,
             INDUSTRYCODE,
             INDUSTRYNAME,
             INDUSTRYSYSTEMCODE,
             DECLAREDATE,
             UTSID)

SELECT * FROM
(
	SELECT
			a.NEWSID,
			a.INDUSTRYCODE,
			a.INDUSTRYNAME,
			a.INDUSTRYSYSTEMCODE,
			b.DECLAREDATE,
			a.UTSID
FROM gta_data.news_industry a INNER JOIN gta_data.news_newsinfo b
ON a.NEWSID = b.NEWSID
    WHERE b.DECLAREDATE >= '2013-02-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_news_newsinfo_lst;

INSERT INTO qic_db.c_news_newsinfo_lst
            (
				NEWSID,
				DECLAREDATE,
				TITLE,
				NEWSSUMMARY,
				NEWSCONTENT,
				KEYWORD,
				NEWSSOURCE,
				AUTOR,
				ISACCESSORY,
				FILESIZE,
				NEWSSOURCEID,
				UTSID
			)
SELECT
	NEWSID,
	DECLAREDATE,
	TITLE,
	NEWSSUMMARY,
	NEWSCONTENT,
	KEYWORD,
	NEWSSOURCE,
	AUTOR,
	ISACCESSORY,
	FILESIZE,
	NEWSSOURCEID,
	UTSID
FROM gta_data.news_newsinfo
WHERE  DECLAREDATE >= '2013-02-01';


/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_news_security_lst;

INSERT INTO qic_db.c_news_security_lst
            (NEWSID,
             SECURITYID,
             SYMBOL,
             SECURITYTYPEID,
             SECURITYTYPE,
             DECLAREDATE,
             UTSID)

SELECT * FROM

(
	SELECT
		a.NEWSID,
		a.SECURITYID,
		a.SYMBOL,
		a.SECURITYTYPEID,
		a.SECURITYTYPE,
		b.DECLAREDATE,
		a.UTSID
	FROM gta_data.news_security a INNER JOIN gta_data.news_newsinfo b
	ON a.NEWSID = b.NEWSID
    WHERE b.DECLAREDATE >= '2013-02-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_rep_category_lst;

INSERT INTO qic_db.c_rep_category_lst
            (REPORTID,
             CATEGORYCODE,
             CATEGORY,
             DECLAREDATE,
             UTSID)

SELECT * FROM

(
	SELECT
		a.REPORTID,
		a.CATEGORYCODE,
		a.CATEGORY,
		b.DECLAREDATE,
		a.UTSID
    FROM gta_data.rep_category a INNER JOIN gta_data.rep_reportinfo b
    ON a.REPORTID = b.REPORTID
    WHERE b.DECLAREDATE >= '2013-01-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_rep_industry_lst;

INSERT INTO qic_db.c_rep_industry_lst
            (REPORTID,
             INDUSTRYCODE,
             INDUSTRYNAME,
             DECLAREDATE,
             UTSID)

SELECT * FROM

(
	SELECT
		a.REPORTID,
		a.INDUSTRYCODE,
		a.INDUSTRYNAME,
		b.DECLAREDATE,
		a.UTSID
    FROM gta_data.rep_industry a INNER JOIN gta_data.rep_reportinfo b
    ON a.REPORTID = b.REPORTID
    WHERE b.DECLAREDATE >= '2013-01-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_rep_institution_lst;

INSERT INTO qic_db.c_rep_institution_lst
            (REPORTID,
             INSTITUTIONID,
             INSTITUTIONNAME,
             DECLAREDATE,
             UTSID)

SELECT * FROM

(
	SELECT
		a.REPORTID,
		a.INSTITUTIONID,
		a.INSTITUTIONNAME,
		b.DECLAREDATE,
		a.UTSID
    FROM gta_data.rep_institution a INNER JOIN gta_data.rep_reportinfo b
    ON a.REPORTID = b.REPORTID
    WHERE b.DECLAREDATE >= '2013-01-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_rep_person_lst;

INSERT INTO qic_db.c_rep_person_lst
            (REPORTID,
             RESEARCHERNAME,
             RESEARCHERID,
             DECLAREDATE,
             UTSID)

SELECT * FROM

(
	SELECT
		a.REPORTID,
		a.RESEARCHERNAME,
		a.RESEARCHERID,
		b.DECLAREDATE,
		a.UTSID
    FROM gta_data.rep_person a INNER JOIN gta_data.rep_reportinfo b
    ON a.REPORTID = b.REPORTID
    WHERE b.DECLAREDATE >= '2013-01-01'
) AS ttt;

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_rep_reportinfo_lst;

INSERT INTO qic_db.c_rep_reportinfo_lst
            (REPORTID,
             TITLE,
             DECLAREDATE,
             REPORTDATE,
             SUMMARY,
             KEYWORDS,
             FILESTORAGEPATH,
             FILETYPE,
             FILESIZE,
             UTSID)

SELECT
	REPORTID,
	TITLE,
	DECLAREDATE,
	REPORTDATE,
	SUMMARY,
	KEYWORDS,
	FILESTORAGEPATH,
	FILETYPE,
	FILESIZE,
	UTSID
FROM gta_data.rep_reportinfo
WHERE  DECLAREDATE >= '2013-01-01';

/*-------------------------------------------------------------*/

TRUNCATE TABLE qic_db.c_rep_security_lst;

INSERT INTO qic_db.c_rep_security_lst
            (REPORTID,
             SECURITYID,
             SYMBOL,
             DECLAREDATE,
             UTSID)

SELECT * FROM

(
	SELECT
		a.REPORTID,
		a.SECURITYID,
		a.SYMBOL,
		b.DECLAREDATE,
		a.UTSID
    FROM gta_data.rep_security a INNER JOIN gta_data.rep_reportinfo b
    ON a.REPORTID = b.REPORTID
    WHERE b.DECLAREDATE >= '2013-01-01'
) AS ttt;

/*-------------------------------------------------------------*/