package com.ledgerline.identity.infrastructure;

import com.ledgerline.identity.application.AdminDashboardStats;
import com.ledgerline.identity.application.AdminDashboardStore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcAdminDashboardStore implements AdminDashboardStore {
    private final JdbcTemplate jdbc;

    public JdbcAdminDashboardStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AdminDashboardStats loadStatistics() {
        return jdbc.queryForObject("""
                select (select count(*) from app_user) as total_users,
                       (select count(*) from app_user where enabled = true) as enabled_users,
                       (select count(*) from app_user where enabled = false) as disabled_users,
                       (select count(*) from financial_account) as total_accounts,
                       (select count(*) from financial_account where active = true) as active_accounts,
                       (select count(*) from financial_account where active = false) as closed_accounts
                """, (rs, row) -> new AdminDashboardStats(
                rs.getLong("total_users"), rs.getLong("enabled_users"), rs.getLong("disabled_users"),
                rs.getLong("total_accounts"), rs.getLong("active_accounts"), rs.getLong("closed_accounts")));
    }
}
