/*
 * Copyright 2014 NAVER Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.navercorp.pinpoint.web.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.navercorp.pinpoint.web.vo.exception.PinpointUserGroupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.navercorp.pinpoint.web.service.UserGroupService;
import com.navercorp.pinpoint.web.vo.UserGroup;
import com.navercorp.pinpoint.web.vo.UserGroupMember;
import com.navercorp.pinpoint.web.vo.UserGroupMemberParam;

/**
 * @author minwoo.jung
 */
@Controller
@RequestMapping(value = "/userGroup")
public class UserGroupController {
    
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    
    private static final String SSO_USER = "SSO_USER";
    public static final String USER_GROUP_ID = "userGroupId";
    public static final String USER_ID = "userId";

    @Autowired
    UserGroupService userGroupService;

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> createUserGroup(@RequestBody UserGroup userGroup, @RequestHeader(value=SSO_USER, required=false) String userId) {
        if (StringUtils.isEmpty(userGroup.getId())) {
            return createErrorMessage("500", "There is not id of userGroup in params to create user group");
        }

        try {
            String userGroupNumber = userGroupService.createUserGroup(userGroup, userId);
            Map<String, String> result = new HashMap<>();
            result.put("number", userGroupNumber);
            return result;
        } catch (PinpointUserGroupException e) {
            logger.error(e.getMessage(), e);
            return createErrorMessage("500", e.getMessage());
        }
    }

    @RequestMapping(method = RequestMethod.DELETE)
    @ResponseBody
    public Map<String, String> deleteUserGroup(@RequestBody UserGroup userGroup, @RequestHeader(value=SSO_USER, required=false) String userId) {
        if (StringUtils.isEmpty(userGroup.getId())) {
            return createErrorMessage("500", "there is id of userGroup in params to delete user group");
        }

        try {
            userGroupService.deleteUserGroup(userGroup, userId);
            Map<String, String> result = new HashMap<>();
            result.put("result", "SUCCESS");
            return result;
        } catch (PinpointUserGroupException e) {
            logger.error(e.getMessage(), e);
            return createErrorMessage("500", e.getMessage());
        }
    }
    
    @RequestMapping(method = RequestMethod.GET)
    @ResponseBody
    public List<UserGroup> getUserGroup(@RequestParam(value=USER_ID, required=false) String userId, @RequestParam(value=USER_GROUP_ID, required=false) String userGroupId) {
        if (!StringUtils.isEmpty(userId)) {
            return userGroupService.selectUserGroupByUserId(userId);
        } else if (!StringUtils.isEmpty(userGroupId)) {
            return userGroupService.selectUserGroupByUserGroupId(userGroupId);
        }
        return userGroupService.selectUserGroup();
    }

    @RequestMapping(value = "/member", method = RequestMethod.POST)
    @ResponseBody
    public Map<String, String> insertUserGroupMember(@RequestBody UserGroupMemberParam userGroupMember, @RequestHeader(value=SSO_USER, required=false) String userId) {
        if (StringUtils.isEmpty(userGroupMember.getMemberId()) || StringUtils.isEmpty(userGroupMember.getUserGroupId())) {
            return createErrorMessage("500", "there is not userGroupId or memberId in params to insert user group member");
        }

        try {
            userGroupService.insertMemberWithCheckAuthority(userGroupMember, userId);
            Map<String, String> result = new HashMap<>();
            result.put("result", "SUCCESS");
            return result;
        } catch (PinpointUserGroupException e) {
            logger.error(e.getMessage(), e);
            return createErrorMessage("500", e.getMessage());
        }
    }

    @RequestMapping(value = "/member", method = RequestMethod.DELETE)
    @ResponseBody
    public Map<String, String> deleteUserGroupMember(@RequestBody UserGroupMemberParam userGroupMember, @RequestHeader(value=SSO_USER, required=false) String userId) {
        if (StringUtils.isEmpty(userGroupMember.getUserGroupId()) || StringUtils.isEmpty(userGroupMember.getMemberId())) {
            return createErrorMessage("500", "there is not userGroupId or memberId in params to delete user group member");
        }

        try {
            userGroupService.deleteMemberWithCheckAuthority(userGroupMember, userId);
            Map<String, String> result = new HashMap<>();
            result.put("result", "SUCCESS");
            return result;
        } catch (PinpointUserGroupException e) {
            logger.error(e.getMessage(), e);
            return createErrorMessage("500", e.getMessage());
        }
    }
    
    @RequestMapping(value = "/member", method = RequestMethod.GET)
    @ResponseBody
    public List<UserGroupMember> getUserGroupMember(@RequestParam(USER_GROUP_ID) String userGroupId) {
        return userGroupService.selectMember(userGroupId);
    }
    
    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Map<String, String> handleException(Exception e) {
        logger.error("Exception occurred while trying to CRUD userGroup information", e);
        return createErrorMessage("500", "Exception occurred while trying to CRUD userGroup information");
    }

    private Map<String, String> createErrorMessage(String code, String errorMessage) {
        Map<String, String> result = new HashMap<>();
        result.put("errorCode", code);
        result.put("errorMessage", errorMessage);
        return result;
    }
}
