package io.github.lucasbp.datagriptoolkit.mysql;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class GrantSummary {
    public record Entry(String kind, String scope, String privileges, String sourceSql) {
        public boolean global() { return scope.matches("\\*\\s*\\.\\s*\\*"); }
    }

    private record Keyword(String text, int start, int end) {
    }

    private GrantSummary() {
    }

    public static List<Entry> summarize(List<String> grants) {
        Objects.requireNonNull(grants, "Provide grants to display.");
        List<Entry> entries = new ArrayList<>(grants.size());
        for (String grant : grants) {
            entries.add(summarizeOne(Objects.requireNonNull(grant, "Grant text must not be null.")));
        }
        return List.copyOf(entries);
    }

    private static Entry summarizeOne(String source) {
        List<Keyword> keywords = topLevelKeywords(source);
        if (keywords == null || keywords.isEmpty()) {
            return detail(source);
        }
        Keyword grant = keywords.getFirst();
        if (!grant.text().equals("GRANT") || !source.substring(0, grant.start()).isBlank()) {
            return detail(source);
        }
        Keyword on = null;
        Keyword to = null;
        for (int i = 1; i < keywords.size(); i++) {
            Keyword keyword = keywords.get(i);
            if (keyword.text().equals("TO")) {
                to = keyword;
                break;
            }
            if (keyword.text().equals("ON")) {
                if (on != null) {
                    return detail(source);
                }
                on = keyword;
            }
        }
        if (to == null || source.substring(to.end()).isBlank()) {
            return detail(source);
        }
        if (on == null) {
            String roles = source.substring(grant.end(), to.start()).strip();
            if (roles.isEmpty()) {
                return detail(source);
            }
            return new Entry("Roles", "Account", roles, source);
        }
        String privileges = source.substring(grant.end(), on.start()).strip();
        String scope = source.substring(on.end(), to.start()).strip();
        if (privileges.isEmpty() || scope.isEmpty()) {
            return detail(source);
        }
        return new Entry("Privilege", scope, privileges, source);
    }

    private static Entry detail(String source) {
        return new Entry("Details", "Original SQL", source, source);
    }

    private static List<Keyword> topLevelKeywords(String sql) {
        List<Keyword> keywords = new ArrayList<>();
        int depth = 0;
        for (int i = 0; i < sql.length();) {
            char current = sql.charAt(i);
            if (current == '\'' || current == '"' || current == '`') {
                char quote = current;
                i++;
                boolean closed = false;
                while (i < sql.length()) {
                    char quoted = sql.charAt(i++);
                    if (quoted == '\\' && quote != '`') {
                        if (i == sql.length()) {
                            return null;
                        }
                        i++;
                    } else if (quoted == quote) {
                        if (i < sql.length() && sql.charAt(i) == quote) {
                            i++;
                        } else {
                            closed = true;
                            break;
                        }
                    }
                }
                if (!closed) {
                    return null;
                }
            } else if (current == '(') {
                depth++;
                i++;
            } else if (current == ')') {
                if (--depth < 0) {
                    return null;
                }
                i++;
            } else if (current == ';') {
                if (depth != 0 || !sql.substring(i + 1).isBlank()) {
                    return null;
                }
                i++;
            } else if (current == '#' || (current == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-')
                    || (current == '/' && i + 1 < sql.length() && sql.charAt(i + 1) == '*')) {
                return null;
            } else if (Character.isLetter(current) || current == '_') {
                int start = i++;
                while (i < sql.length() && (Character.isLetterOrDigit(sql.charAt(i)) || sql.charAt(i) == '_' || sql.charAt(i) == '$')) {
                    i++;
                }
                if (depth == 0) {
                    keywords.add(new Keyword(sql.substring(start, i).toUpperCase(Locale.ROOT), start, i));
                }
            } else {
                i++;
            }
        }
        return depth == 0 ? keywords : null;
    }
}
