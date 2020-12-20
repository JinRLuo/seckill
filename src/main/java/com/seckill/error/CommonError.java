package com.seckill.error;

public interface CommonError {

    //获取错误码
    public int getErrCode();
    //获取错误信息
    public String getErrMsg();
    public CommonError setErrMsg(String errMsg);

}
