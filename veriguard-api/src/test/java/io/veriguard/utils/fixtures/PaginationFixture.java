package io.veriguard.utils.fixtures;

import io.veriguard.database.model.Filters;
import io.veriguard.rest.injector_contract.input.NodeContractSearchPaginationInput;
import io.veriguard.utils.pagination.SearchPaginationInput;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

public class PaginationFixture {

  public static SearchPaginationInput.SearchPaginationInputBuilder getDefault() {
    return SearchPaginationInput.builder().page(0).size(10);
  }

  public static NodeContractSearchPaginationInput getOptioned() {
    NodeContractSearchPaginationInput si = new NodeContractSearchPaginationInput();
    si.setPage(0);
    si.setSize(10);
    return si;
  }

  public static SearchPaginationInput simpleSearchWithAndOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    return getDefault().filterGroup(filterGroup).build();
  }

  public static SearchPaginationInput simpleSearchWithOrOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.or);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    return getDefault().filterGroup(filterGroup).build();
  }

  public static NodeContractSearchPaginationInput optionedSearchWithAndOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.and);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    NodeContractSearchPaginationInput si = getOptioned();
    si.setFilterGroup(filterGroup);
    return si;
  }

  public static NodeContractSearchPaginationInput optionedSearchWithOrOperator(
      String key, String value, Filters.FilterOperator operator) {
    Filters.Filter filter = new Filters.Filter();
    filter.setKey(key);
    filter.setValues(value == null ? List.of() : List.of(value));
    filter.setOperator(operator);
    Filters.FilterGroup filterGroup = new Filters.FilterGroup();
    filterGroup.setMode(Filters.FilterMode.or);
    filterGroup.setFilters(new ArrayList<>(List.of(filter)));
    NodeContractSearchPaginationInput si = getOptioned();
    si.setFilterGroup(filterGroup);
    return si;
  }

  public static <T> Page<T> pagedOutput(List<T> output) {
    return new PageImpl<>(output);
  }
}
