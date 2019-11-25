package lv.chi.photopicker

import lv.chi.photopicker.loader.ImageLoader

object ChiliPhotoPicker {

   /**
    * One time setup for required configurations.
    * Should be called from Application's class onCreate
    *
    * @param loader - ImageLoader implementation
    * @param authority - FileProvider's authority. Is necessary if you allow picker to use Camera,
    *                    so it can store temporary files. Could be null otherwise
    */
    fun setUp(loader: ImageLoader, authority: String? = null) {
        PickerConfiguration.setUp(loader, authority)
    }
}