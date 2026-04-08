package com.market;

import com.market.utils.MailUtils;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@Slf4j
@SpringBootTest
class MailUtilsTest {

    @Autowired
    private MailUtils mailUtils;

    @Test
    void testSendCode() throws Exception {
        String email = "2917820320@qq.com";
        String code = mailUtils.achieveCode();
        log.info("向 {} 发送验证码: {}", email, code);
        mailUtils.sendtoMail(email, code);
    }
}
