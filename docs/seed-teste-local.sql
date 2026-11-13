-- conta de teste local (front). cpf: 92171876743 / email: motorista.teste.front@vanroute.test / senha: Teste614fdd28!
begin;
insert into users(id,name,password_hash,cpf,status,email,phone,role) values ('c20fab2c-428e-405f-a822-0ac8dd811fc7','Motorista Teste Front','$argon2id$v=19$m=65536,t=3,p=4$OEOUtK8MPwevOaVCQayp5w$R81kSMA4UNP62Qh8LPl8tuwCyTtNhZsorMVjlGxZQFY','92171876743','ACTIVE','motorista.teste.front@vanroute.test',null,'ROLE_DRIVER');
insert into drivers(user_id,approval_status,link_code) values ('c20fab2c-428e-405f-a822-0ac8dd811fc7','APPROVED','TSTFRONT1');
insert into terms_acceptances(id,user_id,version,accepted_at) select gen_random_uuid(),'c20fab2c-428e-405f-a822-0ac8dd811fc7',version,now() from (select distinct version from terms_acceptances) v;
insert into driver_schools(driver_id,school_id) values ('c20fab2c-428e-405f-a822-0ac8dd811fc7','18d661f3-384e-4e3d-a5a4-9953cd019b40'),('c20fab2c-428e-405f-a822-0ac8dd811fc7','4261c55a-8376-45fa-8b2e-f4f6ec018212');
insert into contracts(id,periodicity,value,start_date,end_date,status) values ('fb497bb0-f694-4a2e-a73e-815cf1aa689a','MONTHLY',300,current_date,current_date+180,'ACTIVE');
insert into user_driver_contracts(id,responsible_id,driver_id,contract_id,student_id,school_id) values (gen_random_uuid(),'c662627f-09f6-4569-9c67-8f656459e967','c20fab2c-428e-405f-a822-0ac8dd811fc7','fb497bb0-f694-4a2e-a73e-815cf1aa689a','abc44c02-348e-4d9d-a0ac-a45cb810054e','18d661f3-384e-4e3d-a5a4-9953cd019b40');
insert into contracts(id,periodicity,value,start_date,end_date,status) values ('e3e3da75-071d-4188-9737-b36cd0edd50d','MONTHLY',300,current_date,current_date+180,'ACTIVE');
insert into user_driver_contracts(id,responsible_id,driver_id,contract_id,student_id,school_id) values (gen_random_uuid(),'c662627f-09f6-4569-9c67-8f656459e967','c20fab2c-428e-405f-a822-0ac8dd811fc7','e3e3da75-071d-4188-9737-b36cd0edd50d','cba33b2a-e921-4182-b010-6b801d7991a6','18d661f3-384e-4e3d-a5a4-9953cd019b40');
insert into contracts(id,periodicity,value,start_date,end_date,status) values ('22fdb263-51c0-4475-a673-ed55d34b80e4','MONTHLY',300,current_date,current_date+180,'ACTIVE');
insert into user_driver_contracts(id,responsible_id,driver_id,contract_id,student_id,school_id) values (gen_random_uuid(),'c662627f-09f6-4569-9c67-8f656459e967','c20fab2c-428e-405f-a822-0ac8dd811fc7','22fdb263-51c0-4475-a673-ed55d34b80e4','30dd2042-61ad-496a-971a-595db240deeb','18d661f3-384e-4e3d-a5a4-9953cd019b40');
insert into contracts(id,periodicity,value,start_date,end_date,status) values ('ccfa9d35-b5f5-48d9-997c-27cdd124cecb','MONTHLY',300,current_date,current_date+180,'ACTIVE');
insert into user_driver_contracts(id,responsible_id,driver_id,contract_id,student_id,school_id) values (gen_random_uuid(),'c662627f-09f6-4569-9c67-8f656459e967','c20fab2c-428e-405f-a822-0ac8dd811fc7','ccfa9d35-b5f5-48d9-997c-27cdd124cecb','264d30ff-2d9d-4807-96b8-5038801d466f','18d661f3-384e-4e3d-a5a4-9953cd019b40');
insert into contracts(id,periodicity,value,start_date,end_date,status) values ('fcec974b-c7aa-47de-a41d-0c796e32f7a6','MONTHLY',300,current_date,current_date+180,'ACTIVE');
insert into user_driver_contracts(id,responsible_id,driver_id,contract_id,student_id,school_id) values (gen_random_uuid(),'c662627f-09f6-4569-9c67-8f656459e967','c20fab2c-428e-405f-a822-0ac8dd811fc7','fcec974b-c7aa-47de-a41d-0c796e32f7a6','7a2158e7-f6b5-4562-aaf2-3ec6fdbd877f','18d661f3-384e-4e3d-a5a4-9953cd019b40');
insert into contracts(id,periodicity,value,start_date,end_date,status) values ('368c7e1d-8fe7-4bef-bf29-03cc5570da75','MONTHLY',300,current_date,current_date+180,'ACTIVE');
insert into user_driver_contracts(id,responsible_id,driver_id,contract_id,student_id,school_id) values (gen_random_uuid(),'c662627f-09f6-4569-9c67-8f656459e967','c20fab2c-428e-405f-a822-0ac8dd811fc7','368c7e1d-8fe7-4bef-bf29-03cc5570da75','90a3814b-a484-4637-8f80-da2047151724','4261c55a-8376-45fa-8b2e-f4f6ec018212');
commit;

-- responsavel de teste (front). cpf: 77529478575 / email: responsavel.teste.front@vanroute.test / senha: Teste83c06701!
begin;
insert into users(id,name,password_hash,cpf,status,email,role) values ('36f3e6fa-f40c-4850-a4ed-1605c654c95e','Responsável Teste Front','$argon2id$v=19$m=65536,t=3,p=4$eGfS9GU+LJLv8eU9jEScNg$mRen0u177x4ppQfqJ9wJ9ygMj0ePu7+rGszUw8qmy5k','77529478575','ACTIVE','responsavel.teste.front@vanroute.test','ROLE_RESPONSIBLE');
insert into responsibles(user_id) values ('36f3e6fa-f40c-4850-a4ed-1605c654c95e');
insert into terms_acceptances(id,user_id,version,accepted_at) select gen_random_uuid(),'36f3e6fa-f40c-4850-a4ed-1605c654c95e',version,now() from (select distinct version from terms_acceptances) v;
insert into student_responsibles(id,student_id,responsible_id,relation_type,is_admin) values (gen_random_uuid(),'abc44c02-348e-4d9d-a0ac-a45cb810054e','36f3e6fa-f40c-4850-a4ed-1605c654c95e','PARENT',false);
insert into student_responsibles(id,student_id,responsible_id,relation_type,is_admin) values (gen_random_uuid(),'cba33b2a-e921-4182-b010-6b801d7991a6','36f3e6fa-f40c-4850-a4ed-1605c654c95e','PARENT',false);
insert into student_responsibles(id,student_id,responsible_id,relation_type,is_admin) values (gen_random_uuid(),'30dd2042-61ad-496a-971a-595db240deeb','36f3e6fa-f40c-4850-a4ed-1605c654c95e','PARENT',false);
commit;

-- responsavel com e-mail real, pra ver os e-mails do app (mesma senha de teste do responsavel acima)
begin;
insert into users(id,name,password_hash,cpf,status,email,role) values ('5b0e2f4a-7d1c-4e8a-9f3b-2c6d8e1a4b70','Responsável Teste E-mail','$argon2id$v=19$m=65536,t=3,p=4$eGfS9GU+LJLv8eU9jEScNg$mRen0u177x4ppQfqJ9wJ9ygMj0ePu7+rGszUw8qmy5k','52601815906','ACTIVE','henrique.psouza.contato@gmail.com','ROLE_RESPONSIBLE');
insert into responsibles(user_id) values ('5b0e2f4a-7d1c-4e8a-9f3b-2c6d8e1a4b70');
insert into terms_acceptances(id,user_id,version,accepted_at) select gen_random_uuid(),'5b0e2f4a-7d1c-4e8a-9f3b-2c6d8e1a4b70',version,now() from (select distinct version from terms_acceptances) v;
commit;

-- responsavel de teste 2 (front). cpf: 08301661305 / email: responsavel2.teste@vanroute.test / senha: Senha12@
begin;
insert into users(id,name,password_hash,cpf,status,email,role) values ('8d4c1e2a-3f6b-4a9d-b7e1-5c2f9a0d6e31','Responsável Teste 2','$argon2id$v=19$m=19456,t=2,p=1$J7g7Qruf7FSxKdrWDAixAw$KbEqUIC/tf4TB3WFwNC45Z2hC3y08eb47AIiG9yD1fw','08301661305','ACTIVE','responsavel2.teste@vanroute.test','ROLE_RESPONSIBLE');
insert into responsibles(user_id) values ('8d4c1e2a-3f6b-4a9d-b7e1-5c2f9a0d6e31');
insert into terms_acceptances(id,user_id,version,accepted_at) select gen_random_uuid(),'8d4c1e2a-3f6b-4a9d-b7e1-5c2f9a0d6e31',version,now() from (select distinct version from terms_acceptances) v;
commit;

-- contas de teste acima sem endereco: o backend so acha responsavel com endereco (Responsible.address nullable=false)
begin;
with base as (select latitude, longitude from addresses where zip_code='01310100' and number=1000 and latitude is not null limit 1),
novos as (
  insert into addresses(id,street,zip_code,city,neighborhood,number,state,latitude,longitude,address_key)
  select gen_random_uuid(),'Avenida Paulista','01310100','São Paulo','Bela Vista',1000,'SP',b.latitude,b.longitude,'01310100|1000'
  from base b, generate_series(1,3)
  returning id
),
numerados as (select id, row_number() over () as n from novos),
contas as (select user_id, row_number() over (order by user_id) as n from responsibles
           where address_id is null and user_id in ('36f3e6fa-f40c-4850-a4ed-1605c654c95e','5b0e2f4a-7d1c-4e8a-9f3b-2c6d8e1a4b70','8d4c1e2a-3f6b-4a9d-b7e1-5c2f9a0d6e31'))
update responsibles r set address_id = n.id from contas c join numerados n on n.n=c.n where r.user_id=c.user_id;
commit;
