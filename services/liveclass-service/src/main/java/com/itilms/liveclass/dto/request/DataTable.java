package com.itilms.liveclass.dto.request;

import java.util.List;

/** One table of a small database shown with a question: its name, column names and rows of text cells. */
public record DataTable(String name, List<String> columns, List<List<String>> rows) {
}
