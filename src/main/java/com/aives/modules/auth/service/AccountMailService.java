package com.aives.modules.auth.service;

import com.aives.modules.auth.entity.User;

public interface AccountMailService {

    /**
     * Gui thong tin dang nhap (email + mat khau tam) cho tai khoan admin vua tao.
     *
     * @return true neu email duoc gui that; false neu he thong chua bat gui mail (chi ghi log)
     * @throws com.aives.common.exception.BusinessException MAIL_SEND_FAILED neu gui that bai
     */
    boolean sendAccountCreated(User user, String temporaryPassword);
}
