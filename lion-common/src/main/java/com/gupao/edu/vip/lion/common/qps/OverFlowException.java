
package com.gupao.edu.vip.lion.common.qps;

/**
 * cny_note 注意此处异常调用了父类4参的构造函数，是为了对这种业务类型的异常做性能优化
 *          详见简书收藏《Java异常(exception)性能优化》
 *
 */
public final class OverFlowException extends RuntimeException {

    private boolean overMaxLimit = false;

    public OverFlowException() {
        super(null, null, false, false);//cny_note 详见简书收藏《Java异常(exception)性能优化》
    }

    public OverFlowException(boolean overMaxLimit) {
        super(null, null, false, false);
        this.overMaxLimit = overMaxLimit;
    }

    public OverFlowException(String message) {
        super(message, null, false, false);
    }

    public boolean isOverMaxLimit() {
        return overMaxLimit;
    }
}
