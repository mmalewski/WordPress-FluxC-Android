package org.wordpress.android.fluxc.store

import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.wordpress.android.fluxc.Dispatcher
import org.wordpress.android.fluxc.Payload
import org.wordpress.android.fluxc.action.WCProductAction
import org.wordpress.android.fluxc.annotations.action.Action
import org.wordpress.android.fluxc.model.SiteModel
import org.wordpress.android.fluxc.model.WCProductModel
import org.wordpress.android.fluxc.model.WCProductVariationModel
import org.wordpress.android.fluxc.network.BaseRequest.BaseNetworkError
import org.wordpress.android.fluxc.network.rest.wpcom.wc.product.ProductRestClient
import org.wordpress.android.fluxc.persistence.ProductSqlUtils
import org.wordpress.android.fluxc.store.WCProductStore.ProductErrorType.GENERIC_ERROR
import org.wordpress.android.util.AppLog
import org.wordpress.android.util.AppLog.T
import java.util.Locale
import javax.inject.Inject

class WCProductStore @Inject constructor(dispatcher: Dispatcher, private val wcProductRestClient: ProductRestClient)
    : Store(dispatcher) {
    class FetchSingleProductPayload(
        var site: SiteModel,
        var remoteProductId: Long
    ) : Payload<BaseNetworkError>()

    enum class ProductErrorType {
        INVALID_PARAM,
        GENERIC_ERROR;

        companion object {
            private val reverseMap = ProductErrorType.values().associateBy(ProductErrorType::name)
            fun fromString(type: String) = reverseMap[type.toUpperCase(Locale.US)] ?: GENERIC_ERROR
        }
    }

    class ProductError(val type: ProductErrorType = GENERIC_ERROR, val message: String = "") : OnChangedError

    class RemoteProductPayload(
        val product: WCProductModel,
        val site: SiteModel
    ) : Payload<ProductError>() {
        constructor(
            error: ProductError,
            product: WCProductModel,
            site: SiteModel
        ) : this(product, site) {
            this.error = error
        }
    }

    class RemoteProductVariationsPayload(
        val site: SiteModel,
        val remoteProductId: Long,
        val variations: List<WCProductVariationModel> = emptyList()
    ) : Payload<ProductError>() {
        constructor(
            error: ProductError,
            site: SiteModel,
            remoteProductId: Long
        ) : this(site, remoteProductId) {
            this.error = error
        }
    }

    // OnChanged events
    class OnProductChanged(
        var rowsAffected: Int,
        var canLoadMore: Boolean = false
    ) : OnChanged<ProductError>() {
        var causeOfChange: WCProductAction? = null
    }

    /**
     * returns the corresponding product from the database as a [WCProductModel].
     */
    fun getProductByRemoteId(site: SiteModel, remoteProductId: Long): WCProductModel? =
            ProductSqlUtils.getProductByRemoteId(site, remoteProductId)

    /**
     * returns true if the corresponding product exists in the database
     */
    fun geProductExistsByRemoteId(site: SiteModel, remoteProductId: Long) =
            ProductSqlUtils.geProductExistsByRemoteId(site, remoteProductId)

    @Subscribe(threadMode = ThreadMode.ASYNC)
    override fun onAction(action: Action<*>) {
        val actionType = action.type as? WCProductAction ?: return
        when (actionType) {
            // remote actions
            WCProductAction.FETCH_SINGLE_PRODUCT -> fetchSingleProduct(action.payload as FetchSingleProductPayload)

            // remote responses
            WCProductAction.FETCHED_SINGLE_PRODUCT ->
                handleFetchSingleProductCompleted(action.payload as RemoteProductPayload)
        }
    }

    override fun onRegister() = AppLog.d(T.API, "WCProductStore onRegister")

    private fun fetchSingleProduct(payload: FetchSingleProductPayload) {
        with(payload) { wcProductRestClient.fetchSingleProduct(site, remoteProductId) }
    }

    private fun handleFetchSingleProductCompleted(payload: RemoteProductPayload) {
        val onProductChanged: OnProductChanged

        if (payload.isError) {
            onProductChanged = OnProductChanged(0).also { it.error = payload.error }
        } else {
            val rowsAffected = ProductSqlUtils.insertOrUpdateProduct(payload.product)
            onProductChanged = OnProductChanged(rowsAffected)
        }

        onProductChanged.causeOfChange = WCProductAction.FETCH_SINGLE_PRODUCT
        emitChange(onProductChanged)
    }
}
