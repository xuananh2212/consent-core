package vn.com.fis.consentcore.registry.api.usecase;

import vn.com.fis.consentcore.registry.api.query.ConsentSearchCriteria;
import vn.com.fis.consentcore.registry.api.query.PageResult;
import vn.com.fis.consentcore.registry.api.result.ConsentResult;

public interface SearchConsentsUseCase {
    PageResult<ConsentResult> search(ConsentSearchCriteria criteria);
}
