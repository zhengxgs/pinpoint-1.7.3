package com.navercorp.pinpoint.web.alarm;

import com.navercorp.pinpoint.web.alarm.checker.AlarmChecker;
import com.navercorp.pinpoint.web.alarm.utils.WxNotifyUtils;
import com.navercorp.pinpoint.web.service.UserGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Created by zhengxgs on 2017/9/9.
 */
@Service
public class AlarmMessageSenderImpl implements AlarmMessageSender {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private UserGroupService userGroupService;

    @Override
    public void sendSms(AlarmChecker checker, int sequenceCount) {
        List<String> receivers = userGroupService.selectPhoneNumberOfMember(checker.getuserGroupId());
        if (receivers.size() == 0) {
            return;
        }
        List<String> sms = checker.getSmsMessage();
        for (String id : receivers) {
            for (String message : sms) {
                logger.error("send SMS : {}", message);
                // TODO Implement logic for sending SMS
                try {
                    WxNotifyUtils.sendMsg(id, message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    @Override
    public void sendEmail(AlarmChecker checker, int sequenceCount) {

    }
}
