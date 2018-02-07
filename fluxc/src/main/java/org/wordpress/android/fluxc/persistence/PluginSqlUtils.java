package org.wordpress.android.fluxc.persistence;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.wellsql.generated.PluginDirectoryModelTable;
import com.wellsql.generated.SitePluginModelTable;
import com.wellsql.generated.WPOrgPluginModelTable;
import com.yarolegovich.wellsql.WellSql;

import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.model.plugin.PluginDirectoryModel;
import org.wordpress.android.fluxc.model.plugin.PluginDirectoryType;
import org.wordpress.android.fluxc.model.plugin.SitePluginModel;
import org.wordpress.android.fluxc.model.plugin.WPOrgPluginModel;

import java.util.ArrayList;
import java.util.List;

import static com.yarolegovich.wellsql.SelectQuery.ORDER_ASCENDING;

public class PluginSqlUtils {
    public static @NonNull List<SitePluginModel> getSitePlugins(@NonNull SiteModel site) {
        return WellSql.select(SitePluginModel.class)
                .where()
                .equals(SitePluginModelTable.LOCAL_SITE_ID, site.getId())
                .endWhere()
                .orderBy(SitePluginModelTable.DISPLAY_NAME, ORDER_ASCENDING)
                .getAsModel();
    }

    public static void insertOrReplaceSitePlugins(@NonNull SiteModel site, @NonNull List<SitePluginModel> plugins) {
        // Remove previous plugins for this site
        removeSitePlugins(site);
        // Insert new plugins for this site
        for (SitePluginModel sitePluginModel : plugins) {
            sitePluginModel.setLocalSiteId(site.getId());
        }
        WellSql.insert(plugins).asSingleTransaction(true).execute();
    }

    private static void removeSitePlugins(@NonNull SiteModel site) {
        WellSql.delete(SitePluginModel.class)
                .where()
                .equals(SitePluginModelTable.LOCAL_SITE_ID, site.getId())
                .endWhere().execute();
    }

    public static int insertOrUpdateSitePlugin(SitePluginModel plugin) {
        if (plugin == null) {
            return 0;
        }

        List<SitePluginModel> pluginResult = WellSql.select(SitePluginModel.class)
                .where()
                .equals(SitePluginModelTable.ID, plugin.getId())
                .endWhere().getAsModel();
        if (pluginResult.isEmpty()) {
            WellSql.insert(plugin).execute();
            return 1;
        } else {
            int oldId = plugin.getId();
            return WellSql.update(SitePluginModel.class).whereId(oldId)
                    .put(plugin, new UpdateAllExceptId<>(SitePluginModel.class)).execute();
        }
    }

    public static int deleteSitePlugin(SiteModel site, SitePluginModel plugin) {
        if (plugin == null) {
            return 0;
        }
        // The local id of the plugin might not be set if it's coming from a network request,
        // using site id and name is a safer approach here
        return WellSql.delete(SitePluginModel.class)
                .where()
                .equals(SitePluginModelTable.NAME, plugin.getName())
                .equals(SitePluginModelTable.LOCAL_SITE_ID, site.getId())
                .endWhere().execute();
    }

    public static @Nullable SitePluginModel getSitePluginByName(SiteModel site, String name) {
        List<SitePluginModel> result = WellSql.select(SitePluginModel.class)
                .where().equals(SitePluginModelTable.NAME, name)
                .equals(SitePluginModelTable.LOCAL_SITE_ID, site.getId())
                .endWhere().getAsModel();
        return result.isEmpty() ? null : result.get(0);
    }

    public static SitePluginModel getSitePluginBySlug(SiteModel site, String slug) {
        List<SitePluginModel> result = WellSql.select(SitePluginModel.class)
                .where().equals(SitePluginModelTable.SLUG, slug)
                .equals(SitePluginModelTable.LOCAL_SITE_ID, site.getId())
                .endWhere().getAsModel();
        return result.isEmpty() ? null : result.get(0);
    }

    public static @Nullable WPOrgPluginModel getWPOrgPluginBySlug(String slug) {
        List<WPOrgPluginModel> result = WellSql.select(WPOrgPluginModel.class)
                .where().equals(WPOrgPluginModelTable.SLUG, slug)
                .endWhere().getAsModel();
        return result.isEmpty() ? null : result.get(0);
    }

    public static @NonNull List<WPOrgPluginModel> getWPOrgPluginsForDirectory(PluginDirectoryType directoryType) {
        List<PluginDirectoryModel> directoryModels = getPluginDirectoriesForType(directoryType);
        List<WPOrgPluginModel> wpOrgPluginModels = new ArrayList<>(directoryModels.size());
        for (PluginDirectoryModel pluginDirectoryModel : directoryModels) {
            WPOrgPluginModel wpOrgPluginModel = getWPOrgPluginBySlug(pluginDirectoryModel.getSlug());
            if (wpOrgPluginModel != null) {
                wpOrgPluginModels.add(wpOrgPluginModel);
            }
        }
        return wpOrgPluginModels;
    }

    public static int insertOrUpdateWPOrgPlugin(WPOrgPluginModel wpOrgPluginModel) {
        if (wpOrgPluginModel == null) {
            return 0;
        }

        // Slug is the primary key in remote, so we should use that to identify WPOrgPluginModels
        WPOrgPluginModel oldPlugin = getWPOrgPluginBySlug(wpOrgPluginModel.getSlug());

        if (oldPlugin == null) {
            WellSql.insert(wpOrgPluginModel).execute();
            return 1;
        } else {
            int oldId = oldPlugin.getId();
            return WellSql.update(WPOrgPluginModel.class).whereId(oldId)
                    .put(wpOrgPluginModel, new UpdateAllExceptId<>(WPOrgPluginModel.class)).execute();
        }
    }

    public static void insertOrUpdateWPOrgPluginList(List<WPOrgPluginModel> wpOrgPluginModels) {
        if (wpOrgPluginModels == null) {
            return;
        }

        WellSql.giveMeWritableDb().beginTransaction();

        for (WPOrgPluginModel pluginModel : wpOrgPluginModels) {
            insertOrUpdateWPOrgPlugin(pluginModel);
        }

        WellSql.giveMeWritableDb().endTransaction();
    }

    // Plugin Directory

    public static void deletePluginDirectoryForType(PluginDirectoryType directoryType) {
        WellSql.delete(PluginDirectoryModel.class)
                .where()
                .equals(PluginDirectoryModelTable.DIRECTORY_TYPE, directoryType.toString())
                .endWhere().execute();
    }

    public static void insertOrUpdatePluginDirectoryList(List<PluginDirectoryModel> pluginDirectories) {
        if (pluginDirectories == null) {
            return;
        }

        for (PluginDirectoryModel pluginDirectory : pluginDirectories) {
            insertOrUpdatePluginDirectoryModel(pluginDirectory);
        }
    }

    public static int getLastRequestedPageForDirectoryType(PluginDirectoryType directoryType) {
        List<PluginDirectoryModel> list = getPluginDirectoriesForType(directoryType);
        int page = 0;
        for (PluginDirectoryModel pluginDirectoryModel : list) {
            page = Math.max(pluginDirectoryModel.getPage(), page);
        }
        return page;
    }

    private static @NonNull List<PluginDirectoryModel> getPluginDirectoriesForType(PluginDirectoryType directoryType) {
        return WellSql.select(PluginDirectoryModel.class)
                .where()
                .equals(PluginDirectoryModelTable.DIRECTORY_TYPE, directoryType)
                .endWhere()
                .getAsModel();
    }

    private static @Nullable PluginDirectoryModel getPluginDirectoryModel(String directoryType, String slug) {
        List<PluginDirectoryModel> result = WellSql.select(PluginDirectoryModel.class)
                .where()
                .equals(PluginDirectoryModelTable.SLUG, slug)
                .equals(PluginDirectoryModelTable.DIRECTORY_TYPE, directoryType)
                .endWhere()
                .getAsModel();
        return result.isEmpty() ? null : result.get(0);
    }

    private static void insertOrUpdatePluginDirectoryModel(PluginDirectoryModel pluginDirectory) {
        if (pluginDirectory == null) {
            return;
        }

        PluginDirectoryModel existing = getPluginDirectoryModel(pluginDirectory.getDirectoryType(),
                pluginDirectory.getDirectoryType());
        if (existing == null) {
            WellSql.insert(pluginDirectory).execute();
        } else {
            WellSql.update(WPOrgPluginModel.class).whereId(existing.getId())
                    .put(pluginDirectory, new UpdateAllExceptId<>(PluginDirectoryModel.class)).execute();
        }
    }
}
