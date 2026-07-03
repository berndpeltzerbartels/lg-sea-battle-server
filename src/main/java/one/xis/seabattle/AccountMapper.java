package one.xis.seabattle;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

@Mapper
interface AccountMapper {
    AccountMapper INSTANCE = Mappers.getMapper(AccountMapper.class);

    Account toAccount(AccountEntity entity);

    AccountEntity toEntity(Account account);
}
