// Author: Xia Zihang
package sg.edu.nus.wellness.dto;

import java.util.List;

/**
 * Generic wrapper for paginated API responses.
 */
public class PagedResponse<T> {
    public List<T> content;
    public int page;
    public int size;
    public long totalElements;
    public int totalPages;
    public boolean last;

    public PagedResponse(List<T> content, int page, int size, long totalElements, int totalPages, boolean last) {
        this.content = content;
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalPages;
        this.last = last;
    }
}
