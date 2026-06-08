package com.duodian.admin.controller.dto;

import org.springframework.data.domain.Page;

import java.util.List;

public class PagedResponse<T> {
    private List<T> list;
    private List<T> content;
    private Long total;
    private Long totalElements;
    private Integer page;
    private Integer size;
    private Integer totalPages;

    public PagedResponse() {
    }

    public static <T> PagedResponse<T> from(Page<T> page) {
        PagedResponse<T> response = new PagedResponse<>();
        response.setList(page.getContent());
        response.setContent(page.getContent());
        response.setTotal(page.getTotalElements());
        response.setTotalElements(page.getTotalElements());
        response.setPage(page.getNumber() + 1);
        response.setSize(page.getSize());
        response.setTotalPages(page.getTotalPages());
        return response;
    }

    public List<T> getList() { return list; }
    public void setList(List<T> list) { this.list = list; }

    public List<T> getContent() { return content; }
    public void setContent(List<T> content) { this.content = content; }

    public Long getTotal() { return total; }
    public void setTotal(Long total) { this.total = total; }

    public Long getTotalElements() { return totalElements; }
    public void setTotalElements(Long totalElements) { this.totalElements = totalElements; }

    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }

    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
}
