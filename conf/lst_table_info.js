//资讯小表
var lst_table_info =
    [
        {
            "tableName":"ann_announcementinfo",
            "fields":[
                "ANNOUNCEMENTID", "DECLAREDATE", "TITLE", "FILESIZE", "ANNOUNCEMENTTYPE", "ANNOUNCEMENTROUTE"
            ],
            "mainTalbe":"_self"
        },

        {
            "tableName":"ann_classify",
            "fields":[
                "ANNOUNCEMENTID", "CLASSIFYID", "CLASSIFYNAME"
            ],
            "mainTalbe":"ann_announcementinfo",
            "joinColumn":"ANNOUNCEMENTID"
        },

        {
            "tableName":"ann_security",
            "fields":[
                "ANNOUNCEMENTID","SECURITYID","SYMBOL","SECURITYTYPEID","SECURITYTYPE"
            ],
            "mainTalbe":"ann_announcementinfo",
            "joinColumn":"ANNOUNCEMENTID"
        },

        {
            "tableName":"ann_summaryinfo",
            "fields":[
                "ANNOUNCEMENTID","DECLAREDATE","SUMMARYTITLE","SUMMARYCONTENT","SUMMARYSIZE"
            ],
            "mainTalbe":"_self"
        },

        {
            "tableName":"news_accessory",
            "fields":[
                "NEWSID","RANK","FULLNAME","BRIEF","ACCESSORYTYPE","ACCESSORYROUTE","ACCESSORYSIZE"
            ],
            "mainTalbe":"news_newsinfo",
            "joinColumn":"NEWSID"
        },

        {
            "tableName":"news_classify",
            "fields":[
                "NEWSID","CLASSIFYID","CLASSIFYNAME"
            ],
            "mainTalbe":"news_newsinfo",
            "joinColumn":"NEWSID"
        },

        {
            "tableName":"news_industry",
            "fields":[
                "NEWSID","INDUSTRYCODE","INDUSTRYNAME","INDUSTRYSYSTEMCODE"
            ],
            "mainTalbe":"news_newsinfo",
            "joinColumn":"NEWSID"
        },

        {
            "tableName":"news_newsinfo",
            "fields":[
                "NEWSID", "DECLAREDATE", "TITLE", "NEWSSUMMARY","NEWSCONTENT","KEYWORD","NEWSSOURCE","AUTOR",
                "ISACCESSORY","FILESIZE","NEWSSOURCEID"
            ],
            "mainTalbe":"_self"
        },

        {
            "tableName":"news_security",
            "fields":[
                "NEWSID","SECURITYID","SYMBOL","SECURITYTYPEID","SECURITYTYPE"
            ],
            "mainTalbe":"news_newsinfo",
            "joinColumn":"NEWSID"
        },

        {
            "tableName":"rep_category",
            "fields":[
                "REPORTID","CATEGORYCODE","CATEGORY"
            ],
            "mainTalbe":"rep_reportinfo",
            "joinColumn":"REPORTID"
        },

        {
            "tableName":"rep_industry",
            "fields":[
                "REPORTID","INDUSTRYCODE","INDUSTRYNAME"
            ],
            "mainTalbe":"rep_reportinfo",
            "joinColumn":"REPORTID"
        },

        {
            "tableName":"rep_institution",
            "fields":[
                "REPORTID","INSTITUTIONID","INSTITUTIONNAME"
            ],
            "mainTalbe":"rep_reportinfo",
            "joinColumn":"REPORTID"
        },

        {
            "tableName":"rep_person",
            "fields":[
                "REPORTID","RESEARCHERNAME","RESEARCHERID"
            ],
            "mainTalbe":"rep_reportinfo",
            "joinColumn":"REPORTID"
        },

        {
            "tableName":"rep_reportinfo",
            "fields":[
                "REPORTID","TITLE","DECLAREDATE","REPORTDATE","SUMMARY","KEYWORDS","FILESTORAGEPATH",
                "FILETYPE","FILESIZE"
            ],
            "mainTalbe":"_self"
        },

        {
            "tableName":"rep_security",
            "fields":[
                "REPORTID","SECURITYID","SYMBOL"
            ],
            "mainTalbe":"rep_reportinfo",
            "joinColumn":"REPORTID"
        }

    ]