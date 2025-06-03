package com.hmall.item.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.item.constants.ItemMqConstants;
import com.hmall.item.domain.po.Item;
import com.hmall.item.domain.po.ItemDoc;
import com.hmall.item.mapper.ItemMapper;
import com.hmall.item.service.IItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final RabbitTemplate rabbitTemplate;

    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        String sqlStatement = "com.hmall.item.mapper.ItemMapper.updateStock";
        boolean r = false;
        try {
            r = executeBatch(items, (sqlSession, entity) -> sqlSession.update(sqlStatement, entity));
        } catch (Exception e) {
            throw new BizIllegalException("更新库存异常，可能是库存不足!", e);
        }
        if (!r) {
            throw new BizIllegalException("库存不足！");
        }
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        return BeanUtils.copyList(listByIds(ids), ItemDTO.class);
    }

    @Override
    public void restoreStock(List<OrderDetailDTO> items) {
        items.forEach(item -> {item.setNum(-item.getNum());});
        String sqlStatement = "com.hmall.item.mapper.ItemMapper.updateStock";
        boolean r = false;
        try {
            r = executeBatch(items, (sqlSession, entity) -> sqlSession.update(sqlStatement, entity));
        } catch (Exception e) {
            throw new BizIllegalException("更新库存异常", e);
        }
    }

    @Override
    public void addItem(ItemDTO item) {
        save(BeanUtils.copyBean(item, Item.class));
        // 发送mq消息，更新ES库存
        try {
            rabbitTemplate.convertAndSend(ItemMqConstants.ITEM_EXCHANGE_NAME, ItemMqConstants.ITEM_ADD_KEY, item.getId());
        } catch (Exception e) {
            log.error("商品新增到ES库失败商品id:{}", item.getId(), e);
        }
    }

    @Override
    public void updateItem(ItemDTO item) {
        // 不允许修改商品状态，所以强制设置为null，更新时，就会忽略该字段
        item.setStatus(null);
        // 更新
        updateById(BeanUtils.copyBean(item, Item.class));
        try {
            rabbitTemplate.convertAndSend(ItemMqConstants.ITEM_EXCHANGE_NAME, ItemMqConstants.ITEM_UPDATE_KEY, item.getId());
        } catch (Exception e) {
            log.error("更新商品信息到ES库失败商品id:{}", item.getId(), e);
        }
    }

    @Override
    public void deleteItemById(Long id) {
        removeById(id);
        try {
            rabbitTemplate.convertAndSend(ItemMqConstants.ITEM_EXCHANGE_NAME, ItemMqConstants.ITEM_REMOVE_KEY, id);
        } catch (Exception e) {
            log.error("更新商品信息到ES库失败商品id:{}", id, e);
        }
    }
}
