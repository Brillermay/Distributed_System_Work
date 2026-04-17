package com.example.demo.config;

import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 服务发现测试 Controller
 * 用于验证 Nacos 服务注册发现功能
 */
@RestController
@RequestMapping("/api/discovery")
public class DiscoveryTestController {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 获取所有服务列表
     */
    @GetMapping("/services")
    public List<String> getServices() {
        return discoveryClient.getServices();
    }

    /**
     * 获取指定服务的实例列表
     */
    @GetMapping("/instances/{serviceId}")
    public List<ServiceInstance> getServiceInstances(String serviceId) {
        return discoveryClient.getInstances(serviceId);
    }

    /**
     * 获取当前服务实例信息
     */
    @GetMapping("/current")
    public Map<String, Object> getCurrentInstance() {
        Map<String, Object> info = new HashMap<>();
        info.put("serviceId", "seckill-service");
        info.put("timestamp", System.currentTimeMillis());
        info.put("message", "服务运行正常");
        return info;
    }

    /**
     * 测试服务间调用（负载均衡）
     */
    @GetMapping("/call")
    public Map<String, Object> testServiceCall() {
        Map<String, Object> result = new HashMap<>();
        try {
            // 通过服务名调用（负载均衡）
            String url = "http://seckill-service/api/discovery/current";
            Map<String, Object> response = restTemplate.getForObject(url, Map.class);
            result.put("success", true);
            result.put("data", response);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }
}