package com.mg.booth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
public class AsyncConfig {

  @Bean(name = "boothExecutor")
  public Executor boothExecutor() {
    // 单机 MVP：2 线程足够；后面可调大
    return Executors.newFixedThreadPool(2);
  }
}

