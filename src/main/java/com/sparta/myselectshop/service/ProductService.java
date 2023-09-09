package com.sparta.myselectshop.service;

import com.sparta.myselectshop.dto.ProductMypriceRequestDto;
import com.sparta.myselectshop.dto.ProductRequestDto;
import com.sparta.myselectshop.dto.ProductResponseDto;
import com.sparta.myselectshop.entity.*;
import com.sparta.myselectshop.exception.ProductNotFoundException;
import com.sparta.myselectshop.naver.dto.ItemDto;
import com.sparta.myselectshop.repository.FolderRepository;
import com.sparta.myselectshop.repository.ProductFolderRepository;
import com.sparta.myselectshop.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final FolderRepository folderRepository;
    private final ProductFolderRepository productFolderRepository;
    private final MessageSource messageSource;

    public static final int MIN_MY_PRICE = 100;

    public ProductResponseDto createProduct(ProductRequestDto requestDto, User user) {
        Product product = productRepository.save(new Product(requestDto,user));
        return new ProductResponseDto(product);
    }

    @Transactional
    public ProductResponseDto updateProduct(Long id, ProductMypriceRequestDto requestDto) {
        int myprice = requestDto.getMyprice();
        if(myprice < MIN_MY_PRICE) {
            throw new IllegalArgumentException(
                    messageSource.getMessage(
                            "below.min.my.price", // message.properties에서 가져옴
                            new Integer[]{MIN_MY_PRICE}, // below.min.my.price = xxxx{0}xxxx 에서 {} 안을 나타냄
                            "Wrong Price",
                            Locale.getDefault() // 언어 설정(국제화 목적)
                    )
            );
        }

        Product product = productRepository.findById(id).orElseThrow(() ->
                new ProductNotFoundException(messageSource.getMessage(
                        "not.found.product", // message.properties에서 가져옴
                        null, // below.min.my.price = xxxx{0}xxxx 에서 {} 안을 나타냄
                        "Not Found Product",
                        Locale.getDefault() // 언어 설정(국제화 목적)
                ))
        );

        product.update(requestDto); // 변경 감지 후 수정되게 Transactional 걸어 놓음

        return new ProductResponseDto(product);
    }

    @Transactional(readOnly = true)
    public Page<ProductResponseDto> getProducts(User user, int page, int size, String sortBy, boolean isAsc) {
        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        UserRoleEnum userRoleEnum = user.getRole();

        Page<Product> productList;
        if (userRoleEnum == UserRoleEnum.USER) {
            productList = productRepository.findAllByUser(user, pageable);
        } else {
            productList = productRepository.findAll(pageable);
        }

        return productList.map(ProductResponseDto::new); // Page에서 제공해주는 문법
    }

    @Transactional
    public void updateBySearch(Long id, ItemDto itemDto) {
        Product product = productRepository.findById(id).orElseThrow(() ->
                new NullPointerException("해당 상품은 존재하지 않습니다.")
        );

        product.updateByItemDto(itemDto);
    }

    public void addFolder(Long productId, Long folderId, User user) {
        Product product = productRepository.findById(productId).orElseThrow(
                () -> new NullPointerException("해당 상품이 존재하지 않습니다")
        );

        Folder folder = folderRepository.findById(folderId).orElseThrow(
                () -> new NullPointerException("해당 폴더가 존재하지 않습니다")
        );

        if (!product.getUser().getId().equals(user.getId()) || !folder.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("당신의 관심상품이 아니거나, 폴더가 아닙니다.");
        }

        Optional<ProductFolder> overlapFolder = productFolderRepository.findByProductAndFolder(product, folder);

        if (overlapFolder.isPresent()) {
            throw new IllegalArgumentException("중복된 폴더입니다");
        }

        productFolderRepository.save(new ProductFolder(product, folder));
    }

    public Page<ProductResponseDto> getProductsInFolder(Long folderId, int page, int size, String sortBy, boolean isAsc, User user) {
        // 페이징 처리
        Sort.Direction direction = isAsc ? Sort.Direction.ASC : Sort.Direction.DESC;
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Product> productList = productRepository.findAllByUserAndProductFolderList_FolderId(user, folderId, pageable);

        Page<ProductResponseDto> responseDtoList = productList.map(ProductResponseDto::new);

        return responseDtoList;
    }
}
