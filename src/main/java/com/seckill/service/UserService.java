package com.seckill.service;

import com.seckill.error.BusinessException;
import com.seckill.service.model.UserModel;

public interface UserService {

    //通过用户id获取用户对象
    UserModel getUserById(Integer id);

    void register(UserModel userModel) throws BusinessException;

    UserModel validateLogin(String telphone,String password) throws BusinessException;

    UserModel getUserByIdInCache(Integer id);
}
