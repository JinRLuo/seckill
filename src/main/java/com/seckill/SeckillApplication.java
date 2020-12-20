package com.seckill;

import com.seckill.dao.UserDOMapper;
import com.seckill.dataobject.UserDO;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


//扫描com.seckill下的所有spring注解
@SpringBootApplication(scanBasePackages = {"com.seckill"})
@RestController
@MapperScan("com.seckill.dao")
public class SeckillApplication {

	@Autowired
	private UserDOMapper userDOMapper;

	@RequestMapping("/")
	public String home(){
		UserDO userDO = userDOMapper.selectByPrimaryKey(1);
		if(userDO==null){
			return "用户不存在";
		}else
			return userDO.getName();
	}

	public static void main(String[] args) {
		SpringApplication.run(SeckillApplication.class, args);
	}

}
