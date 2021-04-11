package config

/**
 * @module config
 * @author ziyue
 * @usecase as global configuration file for scala
 */
object config {
    final val DEBUG: Boolean = true

    final val XLEN: Int = 32
    final val STORE_BUFFER_DEPTH: Int = 32
    final val LOAD_BUFFER_DEPTH: Int = 32

    final val REG_INDEX_WIDTH: Int = 5


    //>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
    // CP0 Register Configurations
    //<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
    final val CP0_INDEX_WIDTH: Int = 5
    final val CP0_REG_WIDTH: Int = 32

    final val CP0_BADVADDR_INDEX = 8
    final val CP0_COUNT_INDEX = 9
    final val CP0_COMPARE_INDEX = 11
    final val CP0_STATUS_INDEX = 12
    final val CP0_CAUSE_INDEX = 13
    final val CP0_EPC_INDEX = 14

    final val CP0_INT_CAUSE = 0
}
