package com.hkust.marketplace.controllers;

import com.hkust.marketplace.utils.SceneManager;
import javafx.fxml.FXML;

public class WelcomeController {

    @FXML
    private void goBuyer() {
        SceneManager.showBuyerLogin();
    }

    @FXML
    private void goSeller() {
        SceneManager.showSellerLogin();
    }

    @FXML
    private void goAdmin() {
        SceneManager.showAdminLogin();
    }
}
