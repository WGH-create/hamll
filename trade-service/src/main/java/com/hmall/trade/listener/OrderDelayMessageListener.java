package com.hmall.trade.listener;


import com.hmall.api.client.PayClient;
import com.hmall.api.dto.PayOrderDTO;
import com.hmall.trade.constants.MQConstants;
import com.hmall.trade.domain.po.Order;
import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class OrderDelayMessageListener {
    private final IOrderService orderService;
    private final PayClient payClient;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = MQConstants.DELAY_ORDER_QUEUE_NAME),
            exchange = @Exchange(name = MQConstants.DELAY_EXCHANGE_NAME,delayed = "true"),
            key = MQConstants.DELAY_ORDER_KEY
    ))
    public void listenOrderDelayMessage(Long orderId){
       //查询订单
        Order order = orderService.getById(orderId);
        //检查订单状态是否为已支付
        if(order == null || order.getStatus() != 1){
            //订单不存在或者订单已支付
            return;
        }
        //未支付，查询支付流水状态
        PayOrderDTO payOrder = payClient.queryPayOrderByBizOrderNo(orderId);
        //判断是否支付
        if(payOrder != null && payOrder.getStatus() == 3){
            //已支付，标记订单状态为已支付
            orderService.markOrderPaySuccess(orderId);
        }else{
            //未支付，取消订单，并恢复库存
            orderService.cancelOrder(orderId);
        }
    }
}
