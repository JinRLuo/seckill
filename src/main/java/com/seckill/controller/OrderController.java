package com.seckill.controller;

import com.seckill.error.BusinessException;
import com.seckill.error.EmBusinessError;
import com.seckill.mq.MqProducer;
import com.seckill.response.CommonReturnType;
import com.seckill.service.ItemService;
import com.seckill.service.OrderService;
import com.seckill.service.PromoService;
import com.seckill.service.model.OrderModel;
import com.seckill.service.model.UserModel;
import org.apache.commons.lang3.StringUtils;
import org.apache.rocketmq.client.producer.MQProducer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import java.util.concurrent.*;


@Controller
@RequestMapping("/order")
@CrossOrigin(allowCredentials = "true",allowedHeaders = "*") //开启springboot处理ajax跨域请求
public class OrderController extends BaseController{

    @Autowired
    private OrderService orderService;

    @Autowired
    private PromoService promoService;

    @Autowired
    private HttpServletRequest httpServletRequest;

    @Autowired
    private RedisTemplate redisTemplate;

    @Autowired
    private MqProducer mqProducer;

    @Autowired
    private ItemService itemService;

    private ExecutorService executorService;

    @PostConstruct
    public void init(){
        executorService = Executors.newFixedThreadPool(20);

    }


    //封装下单请求
    @RequestMapping(value = "/createorder",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType createOrder(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "amount")Integer amount,
                                        @RequestParam(name = "promoId",required = false)Integer promoId,
                                        @RequestParam(name = "promoToken",required = false)String promoToken) throws BusinessException {

        //Boolean is_login = (Boolean)httpServletRequest.getSession().getAttribute("IS_LOGIN");
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }

        //校验秒杀令牌是否正确
        if(promoId != null){
            String inRedisPromoToken = (String) redisTemplate.opsForValue().get("promo_token_"+promoId+"userid_"+userModel.getId()+"itemid_"+itemId);
            if(inRedisPromoToken == null){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
            if(!StringUtils.equals(promoToken,inRedisPromoToken)){
                throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"秒杀令牌校验失败");
            }
        }

        //同步调用线程池的submit方法
        //拥塞窗口为20的等待队列，用来队列化泄洪
        //同时只有20个线程会创建订单，其他的只能等待
        Future<Object> future = executorService.submit(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                //加入库存流水状态
                String stockLogId = itemService.initStockLog(itemId,amount);
                //通过mqProducer开启异步发送扣减库存消息请求
                //由消息中间件去执行创建订单和扣减库存操作并同步到数据库中
                if(!mqProducer.transactionAsyncReduceStock(userModel.getId(),itemId,promoId,amount,stockLogId)){
                    throw new BusinessException(EmBusinessError.UNKNOWN_ERROR,"下单失败");
                }
                return null;
            }
        });

        try {
            future.get();
        } catch (InterruptedException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        } catch (ExecutionException e) {
            throw new BusinessException(EmBusinessError.UNKNOWN_ERROR);
        }

        return CommonReturnType.create(null);
    }

    @RequestMapping(value = "/generatetoken",method = {RequestMethod.POST},consumes = {CONTENT_TYPE_FORMED})
    @ResponseBody
    public CommonReturnType generateToken(@RequestParam(name = "itemId")Integer itemId,
                                        @RequestParam(name = "promoId")Integer promoId) throws BusinessException {
        //根据token获取用户信息
        String token = httpServletRequest.getParameterMap().get("token")[0];
        if(StringUtils.isEmpty(token)){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }
        UserModel userModel = (UserModel) redisTemplate.opsForValue().get(token);
        if(userModel == null){
            throw new BusinessException(EmBusinessError.USER_NOT_LOGIN);
        }

        //获取秒杀访问令牌
        String promoToken = promoService.generateSecondKillToken(promoId,itemId, userModel.getId());
        if(promoToken==null){
            throw new BusinessException(EmBusinessError.PARAMETER_VALIDATION_ERROR,"生成令牌失败");
        }
        return CommonReturnType.create(promoToken);
    }

}
