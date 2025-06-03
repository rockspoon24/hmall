package com.hmall.item.listener;

import com.hmall.common.utils.BeanUtils;
import com.hmall.item.constants.ItemMqConstants;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.rabbit.annotation.Exchange;
import org.springframework.amqp.rabbit.annotation.Queue;
import org.springframework.amqp.rabbit.annotation.QueueBinding;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ItemMessageListener {

    private final IItemService itemService;

    private final ElasticsearchRestTemplate elasticsearchRestTemplate;

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = ItemMqConstants.ITEM_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = ItemMqConstants.ITEM_EXCHANGE_NAME, type = ExchangeTypes.DIRECT),
            key = {ItemMqConstants.ITEM_ADD_KEY}
    ))
    public void addItem(Long id) {
        // 1.从数据库获取item
        Item item = itemService.getById(id);
        // 2.对数据做转换
        ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
        // 3.将数据插入ES
        elasticsearchRestTemplate.save(itemDoc);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = ItemMqConstants.ITEM_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = ItemMqConstants.ITEM_EXCHANGE_NAME, type = ExchangeTypes.DIRECT),
            key = {ItemMqConstants.ITEM_UPDATE_KEY}
    ))
    public void updateItem(Long id) {
        // 1.从数据库获取item
        Item item = itemService.getById(id);
        // 2.对数据做转换
        ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
        // 3.将数据插入ES
        elasticsearchRestTemplate.save(itemDoc);
    }

    @RabbitListener(bindings = @QueueBinding(
            value = @Queue(name = ItemMqConstants.ITEM_QUEUE_NAME, durable = "true"),
            exchange = @Exchange(name = ItemMqConstants.ITEM_EXCHANGE_NAME, type = ExchangeTypes.DIRECT),
            key = {ItemMqConstants.ITEM_REMOVE_KEY}
    ))
    public void removeItem(Long id) {
        // 1.从数据库获取item
//        Item item = itemService.getById(id);
        // 2.对数据做转换
//        ItemDoc itemDoc = BeanUtils.copyBean(item, ItemDoc.class);
        // 3.将数据插入ES
        elasticsearchRestTemplate.delete(id.toString(), ItemDoc.class);
    }
}
