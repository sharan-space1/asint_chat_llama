sap.ui.define([
    "sap/ui/core/mvc/Controller",
    "sap/ui/model/json/JSONModel"
], function (Controller, JSONModel) {
    "use strict";

    return Controller.extend("com.asint.rag.chatllama.app.controller.Chat", {

        onInit: function () {

            var oData = {
                "data": {
                    "conversation": []
                },
                "metadata": {}
            };

            var oModel = new JSONModel(oData);
            this.getView().setModel(oModel, "mChatLlama");

        },

        onTableUpdateFinished: function () {

            let oScrollContainer = this.getView().byId("scroll");

            if (oScrollContainer.getContent()[0].getDomRef()) {
                oScrollContainer.scrollTo(0, oScrollContainer.getContent()[0].getDomRef().offsetHeight, 1000);
            }

        },

        fnAddConversation: function (sMessage, isMe, sTimeStamp) {

            var mChatLlama = this.getView().getModel("mChatLlama");
            var aConversation = mChatLlama.getProperty("/data/conversation");
            var oConversation = {
                "isMe": isMe,
                "message": sMessage,
                "timeStamp": sTimeStamp ? sTimeStamp : new Date().toLocaleString()
            };

            aConversation.push(oConversation);
            mChatLlama.setProperty("/data/conversation", aConversation);

        },

        fnUpdateLastConversation: function (sMessage, isMe, sTimeStamp) {

            var mChatLlama = this.getView().getModel("mChatLlama");
            var aConversation = mChatLlama.getProperty("/data/conversation");
            var oConversation = aConversation.pop();

            oConversation = Object.assign(oConversation, {
                "isMe": isMe,
                "message": oConversation.message + sMessage,
                "timeStamp": sTimeStamp ? sTimeStamp : new Date().toLocaleString()
            });
            aConversation.push(oConversation);
            mChatLlama.setProperty("/data/conversation", aConversation);

        },

        onClear: function () {

            var mChatLlama = this.getView().getModel("mChatLlama");
            var oInputFlex = this.getView().byId("inputFlex");

            setTimeout(function () {
                mChatLlama.setProperty("/data/conversation", []);
                oInputFlex.setBusy(false);
            }, 100);

        },

        onSend: function () {

            var that = this;
            var oInputFlex = this.getView().byId("inputFlex");
            var sPrompt = this.getView().byId("message").getValue();

            if (sPrompt) {
                this.getView().byId("message").setValue("");

                oInputFlex.setBusy(true);

                setTimeout(function () {
                    that.fnAddConversation(sPrompt, true);
                    that.fnAddConversation("", false, "loading...");

                    that.fnTalkToLlama(sPrompt, function (oChunk) {
                        that.fnUpdateLastConversation(oChunk.message, false, !oChunk.done ? "Writing..." : "");
                        if (oChunk.done) {
                            oInputFlex.setBusy(false);
                        }
                    }, function (sErrorMessage) {
                        that.fnUpdateLastConversation(sErrorMessage, false, "");
                        oInputFlex.setBusy(false);
                    });
                }, 500);
            }

        },

        /**
       * Gets the URL prefix of the app
       * @returns {string} the URL module prefix
       */
        _getUrlModulePrefix() {
            return $.sap.getModulePath(
                this.getOwnerComponent().getManifestEntry("/sap.app/id")
            );
        },

        /**
         * API - fetches CSRF token
         *
         * **Note**: For testing/development, you can turn off CSRF protection in the `xs-app.json`
         * by setting `csrfProtection: false` for `"source": "^/api/(.*)$"` route.
         */
        _apiFetchCsrfToken: async function () {
            if (!this._sCsrfToken) {
                const res = await fetch(`${this._getUrlModulePrefix()}/index.html`, {
                    method: "HEAD",
                    headers: {
                        "X-CSRF-Token": "fetch",
                    },
                    credentials: "same-origin",
                });
                this._sCsrfToken = res.headers.get("x-csrf-token");
            }
            return this._sCsrfToken;
        },

        fnParseResult: function (sResult) {

            try {
                return JSON.parse(sResult);
            } catch (e) {
                return sResult;
            }

        },

        fnIsValidPrompt: function (sPrompt, fnCallback) {

            var that = this;
            var sUrl = "/asint/rest/v1/api/new/prompt";
            var sBody = JSON.stringify({
                "prompt": sPrompt
            });

            fetch(sUrl, {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    "Accept": "application/json",
                },
                body: sBody
            }).then(function (oResponse) {
                if (!oResponse.ok) {
                    throw new Error("Something went wrong, Please try again");
                }
                return oResponse.body.getReader();
            }).then(async function (oReader) {
                var oDecoder = new TextDecoder();
                var sResult = "";
                var fnRead = function () {
                    return oReader.read().then(function (oRes) {
                        if (oRes.done) {
                            return that.fnParseResult(sResult);
                        }
                        sResult += oDecoder.decode(oRes.value, { stream: true });
                        return fnRead();
                    });
                }
                var result = await fnRead();

                if (Array.isArray(result)) {
                    fnCallback({
                        "isValid": true,
                        "message": ""
                    });
                } else {
                    fnCallback({
                        "isValid": false,
                        "message": result.content || result
                    });
                }
            }).catch(function (oError) {
                fnCallback({
                    "isValid": false,
                    "message": "Something went wrong, Please try again"
                });
            });

        },

        fnTalkToLlama: function (sPrompt, fnSuccess, fnError) {

            var that = this;

            this.fnIsValidPrompt(sPrompt, async function (oResponse) {
                if (oResponse.isValid) {
                    var sUrl = "/asint_ai/v1/api/chat";
                    var oHeaders = {
                        "AI-Resource-Group": "default",
                        "Content-Type": "application/json",
                        "X-CSRF-Token": await that._apiFetchCsrfToken()
                    };
                    var sBody = JSON.stringify({
                        "model": "phi3:latest",
                        "messages": [
                            {
                                "role": "user",
                                "content": sPrompt
                            }
                        ],
                        "stream": true
                    });

                    fetch(sUrl, {
                        method: "POST",
                        headers: oHeaders,
                        body: sBody
                    }).then(function (oResponse) {
                        var oReader = oResponse.body.getReader();
                        var sBuffer = "";

                        function fnProcess(oResult) {
                            var isDone = oResult.done;
                            var value = oResult.value;

                            if (isDone) {
                                return fnSuccess({
                                    done: isDone,
                                    message: ""
                                });
                            }

                            sBuffer += new TextDecoder().decode(value, {
                                stream: true
                            });

                            var aRows = sBuffer.split("\n");
                            sBuffer = aRows.pop();

                            aRows.forEach(function (sText) {
                                if (sText.trim()) {
                                    var sTrimmedText = sText.trim().replace(/,$/, "");

                                    try {
                                        var oData = JSON.parse(sTrimmedText);
                                        console.log(oData.message.content);

                                        if (fnSuccess) {
                                            fnSuccess({
                                                done: isDone,
                                                message: oData.message.content
                                            });
                                        }
                                    } catch (oError) {
                                        console.log(oError);
                                    }
                                }
                            });

                            oReader.read().then(fnProcess);
                        }
                        oReader.read().then(fnProcess);
                    }).catch(function () {
                        if (fnError) {
                            fnError("Something went wrong, Please try again");
                        }
                    });
                } else {
                    if (fnError) {
                        fnError(oResponse.message);
                    }
                }
            });

        }

    });

});
