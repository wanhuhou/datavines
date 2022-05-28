/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.datavines.server.coordinator.repository.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import io.datavines.common.dto.user.*;
import io.datavines.server.coordinator.api.enums.ApiStatus;
import io.datavines.server.coordinator.repository.entity.User;
import io.datavines.server.coordinator.repository.entity.WorkSpace;
import io.datavines.server.coordinator.repository.mapper.UserMapper;
import io.datavines.server.coordinator.repository.mapper.WorkSpaceMapper;
import io.datavines.server.coordinator.repository.service.UserService;
import io.datavines.server.exception.DataVinesServerException;
import io.datavines.server.utils.ContextHolder;
import jodd.util.BCrypt;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service("userService")
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private WorkSpaceMapper workSpaceMapper;

    @Override
    public User getByUsername(String username) {
        return baseMapper.selectOne(new QueryWrapper<User>().eq("username",username));
    }

    @Override
    public UserLoginResult login(UserLogin userLogin) throws DataVinesServerException {
        String username = userLogin.getUsername();
        String password = userLogin.getPassword();

        User user = getByUsername(username);
        if (user != null) {

            boolean checkPassword = BCrypt.checkpw(password, user.getPassword());
            if (checkPassword) {
                UserLoginResult result = new UserLoginResult();
                BeanUtils.copyProperties(user, result);
                return result;
            } else {
                log.error("Username({}) password ({}) is wrong", username, password);
                throw new DataVinesServerException(ApiStatus.USERNAME_OR_PASSWORD_ERROR);
            }
        }

        throw new DataVinesServerException(ApiStatus.USERNAME_OR_PASSWORD_ERROR);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserBaseInfo register(UserRegister userRegister) throws DataVinesServerException {
        String username = userRegister.getUsername();

        if(!isUserExist(username)) {
            User user = new User();

            userRegister.setPassword(BCrypt.hashpw(userRegister.getPassword(), BCrypt.gensalt()));
            BeanUtils.copyProperties(userRegister, user);
            user.setCreateTime(LocalDateTime.now());
            user.setUpdateTime(LocalDateTime.now());
            
            if (baseMapper.insert(user) <= 0) {
                log.info("Register fail, userRegister:{}", userRegister);
                throw new DataVinesServerException(ApiStatus.REGISTER_USER_ERROR, username);
            }

            UserBaseInfo userBaseInfo = new UserBaseInfo();
            BeanUtils.copyProperties(user, userBaseInfo);

            //create default workspace
            WorkSpace workSpace = new WorkSpace();
            workSpace.setName(username + "'s default");
            workSpace.setCreateBy(user.getId());
            workSpace.setCreateTime(LocalDateTime.now());
            workSpace.setUpdateBy(user.getId());
            workSpace.setUpdateTime(LocalDateTime.now());

            workSpaceMapper.insert(workSpace);

            return userBaseInfo;
        } else {
            log.info("The username({}) has been registered", username);
            throw new DataVinesServerException(ApiStatus.USERNAME_HAS_BEEN_REGISTERED_ERROR, username);
        }

    }

    @Override
    public Boolean updateUserInfo(UserUpdate userUpdate) {
        return null;
    }

    @Override
    public Boolean resetPassword(UserResetPassword userResetPassword) {
        return null;
    }

    private boolean isUserExist(String username) {
        User user = getByUsername(username);
        return user != null;
    }

}