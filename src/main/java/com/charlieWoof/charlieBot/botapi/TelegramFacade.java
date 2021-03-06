package com.charlieWoof.charlieBot.botapi;

import com.charlieWoof.charlieBot.CharlieWebhookBot;
import com.charlieWoof.charlieBot.cache.UserDataCache;
import com.charlieWoof.charlieBot.cache.UserInfoCache;
import com.charlieWoof.charlieBot.data.entity.Product;
import com.charlieWoof.charlieBot.data.service.CategoryService;
import com.charlieWoof.charlieBot.data.service.ProductService;
import com.charlieWoof.charlieBot.service.MainMenuService;
import com.charlieWoof.charlieBot.service.ShowProductService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageReplyMarkup;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;


@Component
@Slf4j
public class TelegramFacade {
    private BotStateContext botStateContext;
    private UserDataCache userDataCache;
    private MainMenuService mainMenuService;
    private CategoryService categoryService;
    private ProductService productService;
    private ShowProductService showProductService;
    private CharlieWebhookBot charlieWebhookBot;

    public TelegramFacade(BotStateContext botStateContext, UserDataCache userDataCache,MainMenuService mainMenuService,
                          CategoryService categoryService, ShowProductService showProductService,
                          ProductService productService, @Lazy CharlieWebhookBot charlieWebhookBot) {
        this.botStateContext = botStateContext;
        this.userDataCache = userDataCache;
        this.mainMenuService = mainMenuService;
        this.categoryService = categoryService;
        this.showProductService = showProductService;
        this.productService = productService;
        this.charlieWebhookBot = charlieWebhookBot;
    }

    public BotApiMethod<?> handleUpdate(Update update) {
        SendMessage replyMessage = null;

        if (update.hasCallbackQuery()) {
            CallbackQuery callbackQuery = update.getCallbackQuery();
            log.info("New callbackQuery from User: {}, userId: {}, with data: {}", update.getCallbackQuery().getFrom().getUserName(),
                    callbackQuery.getFrom().getId(), update.getCallbackQuery().getData());
            return processCallbackQuery(callbackQuery);
        }

        Message message = update.getMessage();
        if (message != null && message.hasText()) {
            log.info("New message from User:{}, chatId: {},  with text: {}",
                    message.getFrom().getUserName(), message.getChatId(), message.getText());
            replyMessage = handleInputMessage(message);
        }

        return replyMessage;
    }

    private SendMessage handleInputMessage(Message message) {
        String inputMsg = message.getText();
        int userId = message.getFrom().getId();
        BotState botState;
        SendMessage replyMessage;

        switch (inputMsg) {
            case "/start":
                botState = BotState.START;
                break;
            case "Сайт":
                botState = BotState.ASK_WEBSITE;
                break;
            case "Запис на стрижку":
                botState = BotState.ASK_SIGN_UP;
                break;
            case "Послуги та ціни":
                botState = BotState.ASK_PRICE;
                break;
            case "Курси грумінгу":
                botState = BotState.ASK_COURSES;
                break;
            case "Товари":
                botState = BotState.SHOW_CATEGORY;
                break;
            case "Мій кошик":
                botState = BotState.SHOW_BUCKET_INFO;
                break;
            case "Мої замовлення":
                botState = BotState.SHOW_ORDERS;
                break;
            default:
                botState = userDataCache.getUsersCurrentBotState(userId);
                break;
        }

        userDataCache.setUsersCurrentBotState(userId, botState);

        replyMessage = botStateContext.processInputMessage(botState, message);

        return replyMessage;
    }

    private BotApiMethod<?> processCallbackQuery(CallbackQuery buttonQuery) {
        final long chatId = buttonQuery.getMessage().getChatId();
        final int userId = buttonQuery.getFrom().getId();
        BotApiMethod<?> callBackAnswer = mainMenuService.getMainMenuMessage(chatId, "Воспользуйтесь главным меню");


        //From Destiny choose buttons
        if (buttonQuery.getData().contains("categoryId")) {
            int id = Integer.parseInt(buttonQuery.getData().replace("categoryId",""));
            System.out.println(categoryService.findById(id));
            userDataCache.saveUserInfoCache(userId,new UserInfoCache(id,0,5));
            userDataCache.setUsersCurrentBotState(userId, BotState.SHOW_PRODUCTS_BY_CATEGORY);
            showProductService.showProducts(chatId,userId);
            callBackAnswer = null;
        }


        if (buttonQuery.getData().contains("addToBucket")) {
//            int id = Integer.parseInt(buttonQuery.getData().replace("addToBucket",""));
//            Product product = productService.findById(id);
//            System.out.println(product);
//            userDataCache.getUserInfoCache(userId).getProductList().add(product);
//            System.out.println(userDataCache.getUserInfoCache(userId).getProductList());

            showProductService.handleCallbackQuery(buttonQuery);
            callBackAnswer = sendAnswerCallbackQuery("Товар додано",false,buttonQuery);
        }

        if (buttonQuery.getData().equals("clearBucketInfo")) {
            userDataCache.getUserInfoCache(userId).getProductList().clear();
            callBackAnswer = sendAnswerCallbackQuery("Корзина пуста",false,buttonQuery);
        }
        if (buttonQuery.getData().equals("toDoOrder")) {
            userDataCache.getUserInfoCache(userId).getProductList().clear();
            InlineKeyboardMarkup replyMarkup = buttonQuery.getMessage().getReplyMarkup();

            EditMessageReplyMarkup editMessageReplyMarkup = new EditMessageReplyMarkup();
            editMessageReplyMarkup.setChatId(chatId);
            editMessageReplyMarkup.setMessageId(buttonQuery.getMessage().getMessageId());
            editMessageReplyMarkup.setReplyMarkup(getInlineMessageButtons());

            charlieWebhookBot.updateSomeInlineMarkup(editMessageReplyMarkup);

            callBackAnswer = sendAnswerCallbackQuery("Корзина пуста",false,buttonQuery);
        }

        if (buttonQuery.getData().equals("inc") || buttonQuery.getData().equals("dec") || buttonQuery.getData().equals("count")){
            callBackAnswer = showProductService.handleCallbackQuery(buttonQuery);
        }


        return callBackAnswer;
    }
    private InlineKeyboardMarkup getInlineMessageButtons() {
        InlineKeyboardMarkup markupInline = new InlineKeyboardMarkup();
        List <List<InlineKeyboardButton>> rowsInline = new ArrayList<List<InlineKeyboardButton>>();
        List < InlineKeyboardButton > rowInline1 = new ArrayList <InlineKeyboardButton> ();
        List < InlineKeyboardButton > rowInline2 = new ArrayList <InlineKeyboardButton> ();
        InlineKeyboardButton orderBtn = new InlineKeyboardButton().setText("Оформити замовлення");
        InlineKeyboardButton clearBtn = new InlineKeyboardButton().setText("Очистити кошикaweq");

        orderBtn.setCallbackData("toDoOrder");
        clearBtn.setCallbackData("clearBucketInfo");

        rowInline1.add(orderBtn);
        rowInline2.add(clearBtn);

        rowsInline.add(rowInline1);
        rowsInline.add(rowInline2);

        markupInline.setKeyboard(rowsInline);

        return markupInline;
    }


    private AnswerCallbackQuery sendAnswerCallbackQuery(String text, boolean alert, CallbackQuery callbackquery) {
        AnswerCallbackQuery answerCallbackQuery = new AnswerCallbackQuery();
        answerCallbackQuery.setCallbackQueryId(callbackquery.getId());
        answerCallbackQuery.setShowAlert(alert);
        answerCallbackQuery.setText(text);
        return answerCallbackQuery;
    }

}
