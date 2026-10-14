package com.hmall.trade.service.Impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import com.hmall.trade.domain.dto.OrderDetailDTO;
import com.hmall.trade.domain.po.OrderDetail;
import com.hmall.trade.mapper.OrderDetailMapper;
import com.hmall.trade.service.IOrderDetailService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 订单详情表 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2023-05-05
 */
@Service
public class OrderDetailServiceImpl extends ServiceImpl<OrderDetailMapper, OrderDetail> implements IOrderDetailService {

    @Override
    public List<OrderDetail> listByOrderId(Long orderId) {
        LambdaQueryWrapper<OrderDetail> queryWrapper = new LambdaQueryWrapper<OrderDetail>()
                .eq(OrderDetail::getOrderId, orderId);
        // 调用MyBatis-Plus的list方法查询
        return this.list(queryWrapper);
    }
}
