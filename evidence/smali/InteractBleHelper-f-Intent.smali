.method public final f(Landroid/content/Intent;)V
    .registers 31
    .param p1    # Landroid/content/Intent;
        .annotation build Lorg/jetbrains/annotations/NotNull;
        .end annotation
    .end param

    move-object/from16 v0, p0

    move-object/from16 v1, p1

    const-string v2, "0"

    const-string v3, "1"

    const-string v4, "2"

    const-string v6, "intent"

    invoke-static {v1, v6}, Lkotlin/jvm/internal/Intrinsics;->p(Ljava/lang/Object;Ljava/lang/String;)V

    const-string v6, "noBleBluetooth"

    const/4 v7, 0x1

    invoke-static {v6, v7}, Lcom/maxhom/weibu/common/utils/PreferenceUtil;->getBoolean(Ljava/lang/String;Z)Z

    move-result v6

    const-string v7, "3"

    const-string v8, "4"

    const-string v9, "5"

    const-string v10, "6"

    const-string v11, "7"

    const-string v12, "8"

    const-string v13, "9"

    const-string v14, "11"

    const-string v15, "12"

    const-string v5, "13"

    move-object/from16 v16, v2

    const-string v2, "14"

    move-object/from16 v17, v3

    const-string v3, "15"

    move-object/from16 v18, v4

    const-string v4, "16"

    move-object/from16 v19, v7

    const-string v7, "17"

    move-object/from16 v20, v8

    const-string v8, "18"

    move-object/from16 v21, v9

    const-string v9, "19"

    move-object/from16 v22, v10

    const-string v10, "20"

    move-object/from16 v23, v10

    const-string v10, "AloneRhythmData"

    move-object/from16 v24, v11

    const-string v11, "30"

    if-eqz v6, :cond_1b5

    invoke-virtual {v1, v10}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :cond_3ce

    invoke-virtual {v1}, Ljava/lang/String;->hashCode()I

    move-result v6

    const/16 v10, 0x63e

    if-eq v6, v10, :cond_1a4

    const/16 v10, 0x65d

    if-eq v6, v10, :cond_195

    packed-switch v6, :pswitch_data_3d0

    packed-switch v6, :pswitch_data_3e8

    goto/16 :goto_3ce

    :pswitch_6a
    invoke-virtual {v1, v9}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_72

    goto/16 :goto_3ce

    :cond_72
    const-string v1, "49"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_79
    invoke-virtual {v1, v8}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_81

    goto/16 :goto_3ce

    :cond_81
    const-string v1, "48"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_88
    invoke-virtual {v1, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_90

    goto/16 :goto_3ce

    :cond_90
    const-string v1, "47"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_97
    invoke-virtual {v1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_9f

    goto/16 :goto_3ce

    :cond_9f
    const-string v1, "46"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_a6
    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_ae

    goto/16 :goto_3ce

    :cond_ae
    const-string v1, "45"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_b5
    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_bd

    goto/16 :goto_3ce

    :cond_bd
    const-string v1, "44"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_c4
    invoke-virtual {v1, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_cc

    goto/16 :goto_3ce

    :cond_cc
    const-string v1, "43"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_d3
    invoke-virtual {v1, v15}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_db

    goto/16 :goto_3ce

    :cond_db
    const-string v1, "42"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_e2
    invoke-virtual {v1, v14}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_ea

    goto/16 :goto_3ce

    :cond_ea
    const-string v1, "41"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_f1
    invoke-virtual {v1, v13}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_f9

    goto/16 :goto_3ce

    :cond_f9
    const-string v1, "39"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_100
    invoke-virtual {v1, v12}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_108

    goto/16 :goto_3ce

    :cond_108
    const-string v1, "38"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_10f
    move-object/from16 v6, v24

    invoke-virtual {v1, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_119

    goto/16 :goto_3ce

    :cond_119
    const-string v1, "37"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_120
    move-object/from16 v2, v22

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_12a

    goto/16 :goto_3ce

    :cond_12a
    const-string v1, "36"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_131
    move-object/from16 v2, v21

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_13b

    goto/16 :goto_3ce

    :cond_13b
    const-string v1, "35"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_142
    move-object/from16 v2, v20

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_14c

    goto/16 :goto_3ce

    :cond_14c
    const-string v1, "34"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_153
    move-object/from16 v2, v19

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_15d

    goto/16 :goto_3ce

    :cond_15d
    const-string v1, "33"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_164
    move-object/from16 v2, v18

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_16e

    goto/16 :goto_3ce

    :cond_16e
    const-string v1, "32"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_175
    move-object/from16 v2, v17

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_17f

    goto/16 :goto_3ce

    :cond_17f
    const-string v1, "31"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->q(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_186
    move-object/from16 v2, v16

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_190

    goto/16 :goto_3ce

    :cond_190
    invoke-virtual {v0, v11}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->r(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :cond_195
    invoke-virtual {v1, v11}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_19d

    goto/16 :goto_3ce

    :cond_19d
    const-string v1, "00"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->r(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :cond_1a4
    move-object/from16 v2, v23

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1ae

    goto/16 :goto_3ce

    :cond_1ae
    const-string v1, "40"

    invoke-virtual {v0, v1}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->r(Ljava/lang/String;)V

    goto/16 :goto_3ce

    :cond_1b5
    move-object/from16 v28, v16

    move-object/from16 v27, v17

    move-object/from16 v26, v18

    move-object/from16 v25, v23

    move-object/from16 v6, v24

    move-object/from16 v16, v11

    move-object/from16 v11, v22

    sget-object v17, Lcom/maxhom/weibu/ui/device/DeviceConnectionState;->e:Lcom/clj/fastble/data/BleDevice;

    if-eqz v17, :cond_3ce

    invoke-virtual {v1, v10}, Landroid/content/Intent;->getStringExtra(Ljava/lang/String;)Ljava/lang/String;

    move-result-object v1

    if-eqz v1, :cond_3ce

    invoke-virtual {v1}, Ljava/lang/String;->hashCode()I

    move-result v10

    move-object/from16 v22, v11

    const/16 v11, 0x63e

    if-eq v10, v11, :cond_3b8

    const/16 v11, 0x65d

    if-eq v10, v11, :cond_3a1

    packed-switch v10, :pswitch_data_3fe

    packed-switch v10, :pswitch_data_416

    goto/16 :goto_3ce

    :pswitch_1e3
    invoke-virtual {v1, v9}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_1eb

    goto/16 :goto_3ce

    :cond_1eb
    const/4 v9, 0x2

    new-array v1, v9, [B

    fill-array-data v1, :array_42c

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_1fa
    const/4 v9, 0x2

    invoke-virtual {v1, v8}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_203

    goto/16 :goto_3ce

    :cond_203
    new-array v1, v9, [B

    fill-array-data v1, :array_432

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_211
    const/4 v9, 0x2

    invoke-virtual {v1, v7}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_21a

    goto/16 :goto_3ce

    :cond_21a
    new-array v1, v9, [B

    fill-array-data v1, :array_438

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_228
    const/4 v9, 0x2

    invoke-virtual {v1, v4}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_231

    goto/16 :goto_3ce

    :cond_231
    new-array v1, v9, [B

    fill-array-data v1, :array_43e

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_23f
    const/4 v9, 0x2

    invoke-virtual {v1, v3}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_248

    goto/16 :goto_3ce

    :cond_248
    new-array v1, v9, [B

    fill-array-data v1, :array_444

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_256
    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_25f

    goto/16 :goto_3ce

    :cond_25f
    new-array v1, v9, [B

    fill-array-data v1, :array_44a

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_26d
    const/4 v9, 0x2

    invoke-virtual {v1, v5}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_276

    goto/16 :goto_3ce

    :cond_276
    new-array v1, v9, [B

    fill-array-data v1, :array_450

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_284
    const/4 v9, 0x2

    invoke-virtual {v1, v15}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_28d

    goto/16 :goto_3ce

    :cond_28d
    new-array v1, v9, [B

    fill-array-data v1, :array_456

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_29b
    const/4 v9, 0x2

    invoke-virtual {v1, v14}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2a4

    goto/16 :goto_3ce

    :cond_2a4
    new-array v1, v9, [B

    fill-array-data v1, :array_45c

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_2b2
    const/4 v9, 0x2

    invoke-virtual {v1, v13}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2bb

    goto/16 :goto_3ce

    :cond_2bb
    new-array v1, v9, [B

    fill-array-data v1, :array_462

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_2c9
    const/4 v9, 0x2

    invoke-virtual {v1, v12}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2d2

    goto/16 :goto_3ce

    :cond_2d2
    new-array v1, v9, [B

    fill-array-data v1, :array_468

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_2e0
    const/4 v9, 0x2

    invoke-virtual {v1, v6}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_2e9

    goto/16 :goto_3ce

    :cond_2e9
    new-array v1, v9, [B

    fill-array-data v1, :array_46e

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_2f7
    move-object/from16 v2, v22

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_302

    goto/16 :goto_3ce

    :cond_302
    new-array v1, v9, [B

    fill-array-data v1, :array_474

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_310
    move-object/from16 v2, v21

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_31b

    goto/16 :goto_3ce

    :cond_31b
    new-array v1, v9, [B

    fill-array-data v1, :array_47a

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_329
    move-object/from16 v2, v20

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_334

    goto/16 :goto_3ce

    :cond_334
    new-array v1, v9, [B

    fill-array-data v1, :array_480

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_342
    move-object/from16 v2, v19

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_34d

    goto/16 :goto_3ce

    :cond_34d
    new-array v1, v9, [B

    fill-array-data v1, :array_486

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto/16 :goto_3ce

    :pswitch_35b
    move-object/from16 v2, v26

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_366

    goto/16 :goto_3ce

    :cond_366
    new-array v1, v9, [B

    fill-array-data v1, :array_48c

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto :goto_3ce

    :pswitch_373
    move-object/from16 v2, v27

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_37d

    goto :goto_3ce

    :cond_37d
    new-array v1, v9, [B

    fill-array-data v1, :array_492

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto :goto_3ce

    :pswitch_38a
    move-object/from16 v2, v28

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_394

    goto :goto_3ce

    :cond_394
    new-array v1, v9, [B

    fill-array-data v1, :array_498

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto :goto_3ce

    :cond_3a1
    move-object/from16 v2, v16

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_3ab

    goto :goto_3ce

    :cond_3ab
    new-array v1, v9, [B

    fill-array-data v1, :array_49e

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    goto :goto_3ce

    :cond_3b8
    move-object/from16 v2, v25

    const/4 v9, 0x2

    invoke-virtual {v1, v2}, Ljava/lang/String;->equals(Ljava/lang/Object;)Z

    move-result v1

    if-nez v1, :cond_3c2

    goto :goto_3ce

    :cond_3c2
    new-array v1, v9, [B

    fill-array-data v1, :array_4a4

    invoke-static {}, Lcom/maxhom/weibu/common/bluetooth/BleUtils;->getWriteUuidAA1()Ljava/lang/String;

    move-result-object v2

    invoke-virtual {v0, v1, v2}, Lcom/maxhom/weibu/ui/device/play/InteractBleHelper;->t([BLjava/lang/String;)V

    :cond_3ce
    :goto_3ce
    return-void

    nop

    :pswitch_data_3d0
    .packed-switch 0x30
        :pswitch_186
        :pswitch_175
        :pswitch_164
        :pswitch_153
        :pswitch_142
        :pswitch_131
        :pswitch_120
        :pswitch_10f
        :pswitch_100
        :pswitch_f1
    .end packed-switch

    :pswitch_data_3e8
    .packed-switch 0x620
        :pswitch_e2
        :pswitch_d3
        :pswitch_c4
        :pswitch_b5
        :pswitch_a6
        :pswitch_97
        :pswitch_88
        :pswitch_79
        :pswitch_6a
    .end packed-switch

    :pswitch_data_3fe
    .packed-switch 0x30
        :pswitch_38a
        :pswitch_373
        :pswitch_35b
        :pswitch_342
        :pswitch_329
        :pswitch_310
        :pswitch_2f7
        :pswitch_2e0
        :pswitch_2c9
        :pswitch_2b2
    .end packed-switch

    :pswitch_data_416
    .packed-switch 0x620
        :pswitch_29b
        :pswitch_284
        :pswitch_26d
        :pswitch_256
        :pswitch_23f
        :pswitch_228
        :pswitch_211
        :pswitch_1fa
        :pswitch_1e3
    .end packed-switch

    :array_42c
    .array-data 1
        0x49t
        0x0t
    .end array-data

    nop

    :array_432
    .array-data 1
        0x48t
        0x0t
    .end array-data

    nop

    :array_438
    .array-data 1
        0x47t
        0x0t
    .end array-data

    nop

    :array_43e
    .array-data 1
        0x46t
        0x0t
    .end array-data

    nop

    :array_444
    .array-data 1
        0x45t
        0x0t
    .end array-data

    nop

    :array_44a
    .array-data 1
        0x44t
        0x0t
    .end array-data

    nop

    :array_450
    .array-data 1
        0x43t
        0x0t
    .end array-data

    nop

    :array_456
    .array-data 1
        0x42t
        0x0t
    .end array-data

    nop

    :array_45c
    .array-data 1
        0x41t
        0x0t
    .end array-data

    nop

    :array_462
    .array-data 1
        0x39t
        0x0t
    .end array-data

    nop

    :array_468
    .array-data 1
        0x38t
        0x0t
    .end array-data

    nop

    :array_46e
    .array-data 1
        0x37t
        0x0t
    .end array-data

    nop

    :array_474
    .array-data 1
        0x36t
        0x0t
    .end array-data

    nop

    :array_47a
    .array-data 1
        0x35t
        0x0t
    .end array-data

    nop

    :array_480
    .array-data 1
        0x34t
        0x0t
    .end array-data

    nop

    :array_486
    .array-data 1
        0x33t
        0x0t
    .end array-data

    nop

    :array_48c
    .array-data 1
        0x32t
        0x0t
    .end array-data

    nop

    :array_492
    .array-data 1
        0x31t
        0x0t
    .end array-data

    nop

    :array_498
    .array-data 1
        0x30t
        0x0t
    .end array-data

    nop

    :array_49e
    .array-data 1
        0x0t
        0x0t
    .end array-data

    nop

    :array_4a4
    .array-data 1
        0x40t
        0x0t
    .end array-data
.end method
