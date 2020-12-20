package com.seckill;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

@SpringBootTest
class SeckillApplicationTests {

	@Autowired
	DataSource dataSource;

	@Test
	void contextLoads() {

	}

}
