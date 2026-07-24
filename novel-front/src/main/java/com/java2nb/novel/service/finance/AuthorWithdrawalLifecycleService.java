package com.java2nb.novel.service.finance;

public interface AuthorWithdrawalLifecycleService {

    boolean process(AuthorWithdrawalRow withdrawal);
}
