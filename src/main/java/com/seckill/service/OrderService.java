package com.seckill.service;

import com.seckill.error.BusinessException;
import com.seckill.service.model.OrderModel;

public interface OrderService {

    /**
     * 秒杀活动两种实现
     * 1.通过前端url上传过来的秒杀活动id，下单接口内校验对应id是否属于对应商品且活动已开始（更优）
     * 优点：后期可扩展到一个商品同时对应多个秒杀活动
     * 2.直接在下单接口内判断对应的商品是否存在秒杀活动，若存在进行中的则以秒杀活动价格下单
     * 缺点：必须查询一遍数据库才知道当前是不是秒杀活动
     * @param userId
     * @param itemId
     * @param amount
     * @return
     * @throws BusinessException
     */
    OrderModel createOrder(Integer userId,Integer itemId,Integer promoId,Integer amount,String stockLogId) throws BusinessException;

}
