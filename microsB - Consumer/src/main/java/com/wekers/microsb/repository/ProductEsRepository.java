package com.wekers.microsb.repository;

import com.wekers.microsb.document.ProductDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

public interface ProductEsRepository extends ElasticsearchRepository<ProductDocument, String> {

}
