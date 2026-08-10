(function (global) {
    'use strict';

    function request(url, options) {
        var requestOptions = options || {};
        requestOptions.credentials = 'same-origin';
        requestOptions.headers = Object.assign({'Accept': 'application/json'}, requestOptions.headers || {});
        return global.fetch(url, requestOptions)
            .then(function (response) { return response.json(); })
            .then(function (result) {
                if (result.code !== 200) {
                    var error = new Error(result.msg || 'API_ERROR');
                    error.code = result.code;
                    throw error;
                }
                return result.data;
            });
    }

    global.NovelGamificationApi = Object.freeze({
        getProfile: function () {
            return request('/user/gamification/profile');
        },
        getQuests: function () {
            return request('/user/gamification/quests');
        },
        checkIn: function () {
            return request('/user/gamification/check-in', {
                method: 'POST'
            });
        },
        claimQuest: function (questCode) {
            return request('/user/gamification/quests/' + encodeURIComponent(questCode) + '/claim', {
                method: 'POST'
            });
        },
        getMonthlyTicketAccount: function () {
            return request('/user/monthly-tickets');
        },
        getMonthlyTicketSeasons: function () {
            return request('/book/monthly-ticket-seasons');
        },
        getBookMonthlyTicketSummary: function (bookId, seasonId) {
            return request('/book/' + encodeURIComponent(bookId) + '/monthly-ticket-summary?seasonId=' +
                encodeURIComponent(seasonId));
        },
        castMonthlyTicketVote: function (bookId, seasonId, amount, clientRequestId) {
            return request('/book/' + encodeURIComponent(bookId) + '/monthly-ticket-votes', {
                method: 'POST',
                headers: {'Content-Type': 'application/json;charset=UTF-8'},
                body: JSON.stringify({
                    seasonId: seasonId,
                    amount: amount,
                    clientRequestId: clientRequestId
                })
            });
        },
        getMonthlyTicketRanking: function (seasonId, period, page, limit) {
            var query = '?page=' + encodeURIComponent(page || 1) +
                '&limit=' + encodeURIComponent(limit || 20);
            if (seasonId) {
                query += '&seasonId=' + encodeURIComponent(seasonId);
            } else if (period) {
                query += '&period=' + encodeURIComponent(period);
            }
            return request('/book/monthly-ticket-ranking' + query);
        },
        getTicker: function (limit) {
            return request('/gamification/ticker?limit=' + encodeURIComponent(limit || 20));
        },
        getPublicPolicy: function () {
            return request('/gamification/policy');
        },
        updateTickerPreference: function (optOut, expectedVersion) {
            return request('/user/gamification/ticker-preference', {
                method: 'PATCH',
                headers: {'Content-Type': 'application/json;charset=UTF-8'},
                body: JSON.stringify({optOut: optOut, expectedVersion: expectedVersion})
            });
        }
    });
}(window));
