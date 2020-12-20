package com.seckill.service;

import com.seckill.service.model.PromoModel;

public interface PromoService {

     PromoModel getPromoByItemId(Integer itemId);

     void publishPromo(Integer promoId);

     //生成秒杀令牌
     String generateSecondKillToken(Integer promoId,Integer itemId,Integer userId);

}
