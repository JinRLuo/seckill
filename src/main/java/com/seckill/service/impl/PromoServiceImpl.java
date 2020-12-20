package com.seckill.service.impl;

import com.seckill.dao.PromoDOMapper;
import com.seckill.dataobject.PromoDO;
import com.seckill.error.BusinessException;
import com.seckill.error.EmBusinessError;
import com.seckill.service.ItemService;
import com.seckill.service.PromoService;
import com.seckill.service.UserService;
import com.seckill.service.model.ItemModel;
import com.seckill.service.model.PromoModel;
import com.seckill.service.model.UserModel;
import org.joda.time.DateTime;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Date;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class PromoServiceImpl implements PromoService {

    @Autowired
    private PromoDOMapper promoDOMapper;

    @Autowired
    private ItemService itemService;

    @Autowired
    private UserService userService;

    @Autowired
    private RedisTemplate redisTemplate;

    @Override
    public PromoModel getPromoByItemId(Integer itemId) {
        //获取对应商品的秒杀活动信息
        PromoDO promoDO = promoDOMapper.selectByItemId(itemId);
        PromoModel promoModel = convertFromDataObject(promoDO);

        if(promoModel==null){
            return null;
        }
        //判断当前时间是否秒杀时间即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        return promoModel;
    }

    @Override
    public void publishPromo(Integer promoId) {
        //根据id获取活动
        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        if(promoDO == null || promoDO.getItemId() == null || promoDO.getItemId().intValue() == 0){
            return ;
        }
        ItemModel itemModel = itemService.getItemById(promoDO.getItemId());

        //将库存同步到redis内
        redisTemplate.opsForValue().set("promo_item_stock_"+itemModel.getId(),itemModel.getStock());

        //将大闸的限制数字设到redis内 设置令牌限制数为库存的5倍
        redisTemplate.opsForValue().set("promo_door_count_"+promoId,itemModel.getStock() * 5);

    }

    @Override
    public String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId) {
        //判断是否库存已售罄，若售罄key存在，直接返回下单失败
        if(redisTemplate.hasKey("promo_item_stock_invalid_"+itemId)){
            return null;
        }

        PromoDO promoDO = promoDOMapper.selectByPrimaryKey(promoId);
        PromoModel promoModel = convertFromDataObject(promoDO);

        if(promoModel==null){
            return null;
        }
        //判断当前时间是否秒杀时间即将开始或正在进行
        if(promoModel.getStartDate().isAfterNow()){
            promoModel.setStatus(1);
        }else if(promoModel.getEndDate().isBeforeNow()){
            promoModel.setStatus(3);
        }else{
            promoModel.setStatus(2);
        }
        if(promoModel.getStatus().intValue()!=2){
            return null;
        }
        //判断item信息是否存在
        ItemModel itemModel = itemService.getItemByIdInCache(itemId);
        if(itemModel==null){
            return null;
        }
        //判断用户是否存在
        UserModel userModel = userService.getUserByIdInCache(userId);
        if(userModel==null){
            return null;
        }

        //获取秒杀大闸的count数量
        long result = redisTemplate.opsForValue().increment("promo_door_count_"+promoId);
        if(result < 0){
            return null;
        }

        //生成token并存入redis内
        String token = UUID.randomUUID().toString().replace("-","");

        redisTemplate.opsForValue().set("promo_token_"+promoId+"userid_"+userId+"itemid_"+itemId,token);
        redisTemplate.expire("promo_token_"+promoId+"userid_"+userId+"itemid_"+itemId,5, TimeUnit.MINUTES);
        return token;
    }

    private PromoModel convertFromDataObject(PromoDO promoDO){
        if(promoDO == null){
            return null;
        }
        PromoModel promoModel = new PromoModel();
        BeanUtils.copyProperties(promoDO,promoModel);
        promoModel.setPromoItemPrice(new BigDecimal(promoDO.getPromoItemPrice()));
        promoModel.setStartDate(new DateTime(promoDO.getStartDate()));
        promoModel.setEndDate(new DateTime(promoDO.getEndDate()));
        return promoModel;
    }

}
