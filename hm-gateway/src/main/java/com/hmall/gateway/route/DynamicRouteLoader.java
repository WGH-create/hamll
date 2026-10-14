package com.hmall.gateway.route;

import cn.hutool.json.JSONUtil;
import com.alibaba.cloud.nacos.NacosConfigManager;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.route.RouteDefinition;
import org.springframework.cloud.gateway.route.RouteDefinitionWriter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import javax.annotation.PostConstruct;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Component
@Slf4j
@RequiredArgsConstructor
public class DynamicRouteLoader {

    private final NacosConfigManager nacosConfigManager;

    private final RouteDefinitionWriter writer;

    private final String dataId = "gateway-routes.json"; //路由配置文件的Data ID
    private final String group = "DEFAULT_GROUP"; //路由配置文件分组

    private final Set<String> routeIds = new HashSet<>();

    @PostConstruct//当前类初始化完成后执行一次
    public void initRouteConfigListener() throws NacosException {
        //首次拉取配置，并添加监听器
        String configInfo = nacosConfigManager.getConfigService().getConfigAndSignListener(dataId, group, 5000, new Listener() {
            @Override
            public Executor getExecutor() {
                return null;
            }

            @Override
            public void receiveConfigInfo(String configInfo) {
               //监听到配置变更，更新配置
                updateConfigInfo(configInfo);
            }
        });
        //首次启动时，更新一次配置
        updateConfigInfo(configInfo);
    }

    public void updateConfigInfo(String configInfo){
        log.debug("监听到路由配置变更，{}", configInfo);
        //反序列化，转成json
        List<RouteDefinition> routeDefinitions = JSONUtil.toList(configInfo, RouteDefinition.class);
        // 更新前先清空旧路由
        // 清除旧路由
        for (String routeId : routeIds) {
            writer.delete(Mono.just(routeId)).subscribe();
        }
        routeIds.clear();

        //更新路由表
        for (RouteDefinition routeDefinition : routeDefinitions) {
            //更新路由
          writer.save(Mono.just(routeDefinition)).subscribe();
          //记录路由id，方便将来删除
          routeIds.add(routeDefinition.getId());
        }
    }
}
