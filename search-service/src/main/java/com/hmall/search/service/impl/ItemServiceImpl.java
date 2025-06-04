package com.hmall.search.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmall.api.dto.ItemDTO;
import com.hmall.api.dto.OrderDetailDTO;
import com.hmall.common.domain.PageDTO;
import com.hmall.common.exception.BizIllegalException;
import com.hmall.common.utils.BeanUtils;
import com.hmall.search.domain.po.Item;
import com.hmall.search.domain.po.ItemDoc;
import com.hmall.search.domain.query.ItemPageQuery;
import com.hmall.search.mapper.ItemMapper;
import com.hmall.search.service.IItemService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.elasticsearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchRestTemplate;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 * @author 虎哥
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final ElasticsearchRestTemplate elasticsearchRestTemplate;

    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        NativeSearchQueryBuilder nativeSearchQueryBuilder = new NativeSearchQueryBuilder();
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        // 查询条件
        if (StrUtil.isNotBlank(query.getKey())) {
            boolQueryBuilder.must(QueryBuilders.matchQuery("key", query.getKey()));
        }
        if (StrUtil.isNotBlank(query.getCategory())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("category", query.getCategory()));
        }
        if (StrUtil.isNotBlank(query.getBrand())) {
            boolQueryBuilder.filter(QueryBuilders.termQuery("brand", query.getBrand()));
        }
        if (query.getMinPrice() != null && query.getMaxPrice() != null) {
            boolQueryBuilder.filter(
                    QueryBuilders.rangeQuery("price").gte(query.getMinPrice()).lte(query.getMaxPrice()));
        } else if (query.getMaxPrice() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").lte(query.getMaxPrice()));
        } else if (query.getMinPrice() != null) {
            boolQueryBuilder.filter(QueryBuilders.rangeQuery("price").gte(query.getMinPrice()));
        }
        if (!boolQueryBuilder.hasClauses()) {
            nativeSearchQueryBuilder.withQuery(boolQueryBuilder);
        } else {
            nativeSearchQueryBuilder.withQuery(QueryBuilders.matchAllQuery());
        }
        // 查询分页
        if (StrUtil.isNotBlank(query.getSortBy())) {
            nativeSearchQueryBuilder
                    .withSort(SortBuilders.fieldSort(query.getSortBy())
                            .order(query.getIsAsc() ? SortOrder.ASC : SortOrder.DESC));
        }
        nativeSearchQueryBuilder.withPageable(PageRequest.of(query.getPageNo() - 1, query.getPageSize()));
        // 查询结果处理
        SearchHits<ItemDoc> hits = elasticsearchRestTemplate.search(nativeSearchQueryBuilder.build(), ItemDoc.class);
        PageDTO<ItemDTO> itemDocPageDTO = new PageDTO<>();
        Long total = hits.getTotalHits() / query.getPageSize();
        itemDocPageDTO.setTotal(total);
        itemDocPageDTO.setPages(query.getPageNo() < total ? Long.valueOf(query.getPageNo()) : total);
        itemDocPageDTO.setList(hits.getSearchHits().stream()
                .map(searchHit -> BeanUtils.copyProperties(searchHit.getContent(), ItemDTO.class))
                .collect(Collectors.toList()));
        return itemDocPageDTO;
    }
}
