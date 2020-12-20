package com.seckill.controller;


import com.seckill.controller.viewObject.ItemVO;
import com.seckill.error.BusinessException;
import com.seckill.response.CommonReturnType;
import com.seckill.service.CacheService;
import com.seckill.service.ItemService;
import com.seckill.service.PromoService;
import com.seckill.service.model.ItemModel;
import org.joda.time.format.DateTimeFormat;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/item")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*") //开启springboot处理ajax跨域请求
public class ItemController extends BaseController{

    @Autowired
    ItemService itemService;

    @Autowired
    RedisTemplate redisTemplate;

    @Autowired
    CacheService cacheService;

    @Autowired
    PromoService promoService;

    //创建商品的controller
    @RequestMapping(value = "/create",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createItem(ItemModel itemModel) throws BusinessException {
        ItemModel itemModelForReturn = itemService.createItem(itemModel);
        ItemVO itemVO = this.convertVOFromModel(itemModelForReturn);
        return CommonReturnType.create(itemVO);
    }

    private ItemVO convertVOFromModel(ItemModel itemModel){
        if(itemModel==null){
            return null;
        }
        ItemVO itemVO = new ItemVO();
        BeanUtils.copyProperties(itemModel,itemVO);
        if(itemModel.getPromoModel()!=null){
            itemVO.setPromoStatus(itemModel.getPromoModel().getStatus());
            itemVO.setPromoId(itemModel.getPromoModel().getId());
            itemVO.setStartDate(itemModel.getPromoModel().getStartDate().toString(DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss")));
            itemVO.setPromoPrice(itemModel.getPromoModel().getPromoItemPrice());
        }else{
            itemVO.setPromoStatus(0);
        }
        return itemVO;
    }

    //商品详情页浏览
    @RequestMapping(value = "/get",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType getItem(@RequestParam(name = "id") Integer id){
        ItemModel itemModel = null;

        //先从本地缓存中取
        itemModel = (ItemModel) cacheService.getFromCommonCache("item"+id);
        if(itemModel == null){
            //不在本地缓存再到redis内取
            itemModel = (ItemModel) redisTemplate.opsForValue().get("item_"+id);

            //不在redis内则从数据库获取
            if(itemModel == null){
                itemModel = itemService.getItemById(id);
                //将itemModel存入redis
                redisTemplate.opsForValue().set("item_"+id,itemModel);
                redisTemplate.expire("item_"+id,10, TimeUnit.MINUTES);
            }
            //填充本地缓存
            cacheService.setCommonCache("item"+id,itemModel);
        }



        ItemVO itemVO = this.convertVOFromModel(itemModel);
        return CommonReturnType.create(itemVO);
    }

    @RequestMapping(value = "/list",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType listItem(){
        List<ItemModel> itemModelList = itemService.listItem();

        List<ItemVO> itemVOList = itemModelList.stream().map(itemModel -> {
            ItemVO itemVO = this.convertVOFromModel(itemModel);
            return itemVO;
        }).collect(Collectors.toList());
        return CommonReturnType.create(itemVOList);
    }

    //发布活动，将活动对应商品的库存加载到缓存中
    @RequestMapping(value = "/publishpromo",method = {RequestMethod.GET})
    @ResponseBody
    public CommonReturnType publishPromo(@RequestParam(name = "id") Integer id){
        promoService.publishPromo(id);
        return CommonReturnType.create(null);
    }
}
