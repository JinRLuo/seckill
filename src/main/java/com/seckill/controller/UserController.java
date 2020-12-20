package com.seckill.controller;

import com.alibaba.druid.util.StringUtils;
import com.seckill.controller.viewObject.UserVO;
import com.seckill.error.BusinessException;
import com.seckill.error.EmBusinessError;
import com.seckill.response.CommonReturnType;
import com.seckill.service.UserService;
import com.seckill.service.model.UserModel;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import sun.misc.BASE64Encoder;

import javax.servlet.http.HttpServletRequest;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Controller
@RequestMapping("/user")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*") //开启springboot处理ajax跨域请求
public class UserController extends BaseController{

    @Autowired
    UserService userService;

    @Autowired
    HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @RequestMapping(value = "/login",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType login(@RequestParam(name="telphone") String telphone,
                                  @RequestParam(name = "password") String password) throws BusinessException, NoSuchAlgorithmException, UnsupportedEncodingException {
        //入参校验
        if(StringUtils.isEmpty(telphone)||StringUtils.isEmpty(password)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR);
        }

        //登录校验
        UserModel userModel = userService.validateLogin(telphone,this.EncodeByMd5(password));

        //生成登录凭证token，UUID
        String uuidToken = UUID.randomUUID().toString();
        uuidToken = uuidToken.replace("-","");
        //将用户的登录信息和登录凭证一起存到redis中
        redisTemplate.opsForValue().set(uuidToken,userModel);
        redisTemplate.expire(uuidToken,1, TimeUnit.HOURS);

        //将登录凭证加入到用户登录成功的session内
//        this.httpServletRequest.getSession().setAttribute("IS_LOGIN",true);
//        this.httpServletRequest.getSession().setAttribute("LOGIN_USER",userModel);

        //下发token
        return CommonReturnType.create(uuidToken);
    }

    @RequestMapping(value = "/register",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType register(@RequestParam(name = "telphone")String telphone,
                                     @RequestParam(name = "otpCode")String otpCode,
                                     @RequestParam(name = "name")String name,
                                     @RequestParam(name = "gender")Integer gender,
                                     @RequestParam(name = "age")Integer age,
                                     @RequestParam(name = "password")String password) throws BusinessException, UnsupportedEncodingException, NoSuchAlgorithmException {
        //验证手机号和对应的otpcode是否符合
        String inSessionOtpCode = (String)httpServletRequest.getSession().getAttribute(telphone);
        if(!StringUtils.equals(otpCode,inSessionOtpCode)){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"短信验证码不符合");
        }
        //用户注册流程
        UserModel userModel = new UserModel();
        userModel.setName(name);
        userModel.setGender(gender);
        userModel.setAge(age);
        userModel.setTelphone(telphone);
        userModel.setRegisterMode("byphone");
        userModel.setEncrptPassword(this.EncodeByMd5(password));

        userService.register(userModel);
        return CommonReturnType.create(null);
    }

    public String EncodeByMd5(String str) throws NoSuchAlgorithmException, UnsupportedEncodingException{
        //确定计算方法
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        BASE64Encoder base64en = new BASE64Encoder();
        //加密字符串
        String newstr = base64en.encode(md5.digest(str.getBytes("utf-8")));
        return newstr;
    }

    //用户获取otp短信接口
    @RequestMapping(value = "/getotp",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType getOtp(@RequestParam(name = "telphone") String telphone){
        //需要按照一定的规则生成OTP验证码
        Random random = new Random();
        int randomInt = random.nextInt(99999);
        randomInt += 10000;
        String otpCode = String.valueOf(randomInt);
        //将OTP验证码同对应用户的手机号关联
        httpServletRequest.getSession().setAttribute(telphone,otpCode);

        //将OTP验证码通过短信通道发送给用户
        System.out.println("telphone = "+telphone+" & otpCode = "+otpCode);

        return CommonReturnType.create(null);
    }

    @RequestMapping("/get")
    @ResponseBody
    public CommonReturnType getUser(@RequestParam(name = "id")Integer id) throws BusinessException{
        //调用service服务获取对应id的用户对象并放回给前端
        UserModel userModel = userService.getUserById(id);

        if(userModel==null){
            throw new BusinessException(EmBusinessError.USER_NOT_EXIST);
        }

        UserVO userVo = convertFromModel(userModel);
        //返回通用对象
        return CommonReturnType.create(userVo);
    }

    //将核心领域模型用户对象转化为可供ui使用的viewobject
    private UserVO convertFromModel(UserModel userModel){
        if(userModel == null){
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(userModel,userVO);
        return userVO;
    }

}
