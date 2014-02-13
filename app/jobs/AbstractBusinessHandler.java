package jobs;

import models.RecordChangeMsg;
import play.Logger;
import play.jobs.Job;

import static util.F10ScheduleSysConst.*;

/**
 * 这个是业务处理(表消息变化)处理器基类
 * User: wenzhihong
 * Date: 12-9-28
 * Time: 上午10:34
 */
public abstract class AbstractBusinessHandler extends Job<HandleResult> {
    protected RecordChangeMsg recordChangeInfo;

    public AbstractBusinessHandler(RecordChangeMsg recordChangeInfo) {
        this.recordChangeInfo = recordChangeInfo;
    }

    @Override
    public HandleResult doJobWithResult() throws Exception {
        HandleResult result = HandleResult.OK;

        if (recordChangeInfo != null) {
            if (INSERT_ACTION.equalsIgnoreCase(recordChangeInfo.action)) {
                result = processInsertAction();
            } else if (UPDATE_ACTION.equalsIgnoreCase(recordChangeInfo.action)) {
                result = processUpdateAction();
            } else if (DEL_ACTION.equalsIgnoreCase(recordChangeInfo.action)) {
                result = processDelAction();
            } else {
                if (Logger.isDebugEnabled()) {
                    Logger.debug("action=%s", recordChangeInfo.action);
                }
            }
        } else {
            Logger.error("没有设置RecordChangeMsg,不能处理 %s", this.toString());
            result = HandleResult.NO_HANDLE;
        }

        return result;
    }

    /**
     * 处理 删除
     */
    abstract protected HandleResult processDelAction();

    /**
     * 处理 update
     */
    abstract protected HandleResult processUpdateAction();

    /**
     * 处理 insert
     */
    abstract protected HandleResult processInsertAction();
}
